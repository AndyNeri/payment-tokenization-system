# Contrato de la API

- Base: `/api/v1` (salvo `/ping` y `/actuator/health`).
- Formato: JSON UTF-8. Errores en **ProblemDetail (RFC 9457)**, `Content-Type: application/problem+json`.
- Trazabilidad: header **`X-Transaction-Id`** (UUID). Si el cliente lo envía, se respeta; si no, se genera. Siempre se devuelve en la respuesta.
- La documentación interactiva (OpenAPI 3.1 / Swagger UI vía springdoc) estará en `/swagger-ui.html`.

## Autenticación

| Esquema | Header | Uso |
|---|---|---|
| API Key | `X-API-Key: <llave>` | Integración entre sistemas: tokenización, auditoría |
| JWT | `Authorization: Bearer <jwt>` | Operaciones del cliente; se obtiene en `POST /auth/login` |
| Público | — | `ping`, registro, login, health |

## Resumen de endpoints

| Método | Ruta | Auth | Éxito | Errores principales |
|---|---|---|---|---|
| GET | `/ping` | público | 200 `pong` | — |
| GET | `/api/v1/{slice}/ping` | público | 200 `pong` | — |
| POST | `/api/v1/customers` | público | 201 | 400, 409 |
| POST | `/api/v1/auth/login` | público | 200 | 400, 401 |
| GET | `/api/v1/me` | JWT | 200 | 401 |
| POST | `/api/v1/tokens` | API Key | 201 | 400, 401, 404, 422 |
| GET | `/api/v1/me/cards` | JWT | 200 | 401 |
| GET | `/api/v1/products?q=&page=&size=` | JWT | 200 | 400, 401 |
| GET | `/api/v1/me/cart` | JWT | 200 | 401 |
| POST | `/api/v1/me/cart/items` | JWT | 200 | 400, 401, 404, 409 |
| DELETE | `/api/v1/me/cart/items/{productId}` | JWT | 200 | 401, 404 |
| POST | `/api/v1/orders` | JWT | 201 | 400, 401, 404, 409 |
| GET | `/api/v1/orders/{orderId}` | JWT | 200 | 401, 404 |
| GET | `/api/v1/audit?transactionId=` | API Key | 200 | 400, 401 |

`{slice}` ∈ `tokens`, `customers`, `products`, `cart`, `orders`.

---

## Ping
```http
GET /ping
```
```
200 OK
Content-Type: text/plain

pong
```

## Clientes

### Registrar cliente
```http
POST /api/v1/customers
Content-Type: application/json

{
  "firstName": "Andrés",
  "lastName": "Neri",
  "email": "andres@example.com",
  "phone": "+584121234567",
  "address": "Av. Principal 123, Caracas",
  "password": "S3gura!2026"
}
```
Validaciones:
- Nombres de 1 a 100 caracteres.
- Email con formato RFC y normalizado a minúsculas.
- Teléfono en formato E.164.
- Dirección de 5 a 255 caracteres.
- Contraseña de al menos 8 caracteres, con mayúscula, minúscula y dígito.

```http
201 Created
Location: /api/v1/me

{
  "id": "0193a1b2-...",
  "firstName": "Andrés",
  "lastName": "Neri",
  "email": "andres@example.com",
  "phone": "+584121234567",
  "address": "Av. Principal 123, Caracas",
  "createdAt": "2026-11-06T15:00:00Z"
}
```
- `409 EMAIL_ALREADY_EXISTS` / `409 PHONE_ALREADY_EXISTS`

## Autenticación

### Login
```http
POST /api/v1/auth/login
{ "email": "andres@example.com", "password": "S3gura!2026" }
```
```http
200 OK
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 1800 }
```
- `401 INVALID_CREDENTIALS`. Es el mismo error para un email inexistente que para una contraseña incorrecta, para no revelar qué cuentas existen.

### Perfil
`GET /api/v1/me` → 200 con el mismo cuerpo que el registro.

## Tokenización

### Tokenizar tarjeta
```http
POST /api/v1/tokens
X-API-Key: <llave>

{
  "customerId": "0193a1b2-...",
  "cardNumber": "4111111111111111",
  "cvv": "123",
  "expirationMonth": 12,
  "expirationYear": 2029,
  "holderName": "ANDRES NERI"
}
```
Validaciones (en este orden):
1. Bean Validation: campos obligatorios y formatos.
2. Luhn, longitud de 13 a 19 dígitos y marca reconocida.
3. Expiración futura.
4. CVV de 3 dígitos (4 para AMEX).
5. Que el cliente exista.
6. Probabilidad de rechazo.

```http
201 Created
{
  "token": "tok_Vx2kq9...",
  "last4": "1111",
  "brand": "VISA",
  "expirationMonth": 12,
  "expirationYear": 2029,
  "createdAt": "2026-11-06T15:05:00Z"
}
```
- `400 INVALID_CARD` (con `errors[]`): Luhn, fecha o CVV inválidos.
- `401 INVALID_API_KEY`
- `404 CUSTOMER_NOT_FOUND`
- `422 TOKENIZATION_REJECTED`: rechazo simulado según `app.tokenization.rejection-probability`.

> La respuesta nunca incluye el PAN completo ni el CVV.

### Tarjetas del cliente
```http
GET /api/v1/me/cards
Authorization: Bearer <jwt>

200 OK
[ { "token": "tok_Vx2kq9...", "last4": "1111", "brand": "VISA", "expirationMonth": 12, "expirationYear": 2029 } ]
```

## Productos

### Buscar
```http
GET /api/v1/products?q=acetaminofen&page=0&size=20
Authorization: Bearer <jwt>
```
- `q` es opcional (de 1 a 100 caracteres) y se busca sin distinguir mayúsculas en nombre y descripción.
- Solo se devuelven productos con `stock > app.product.min-stock`.
- La búsqueda se registra **de forma asíncrona** en `search_logs`. Si ese registro falla, la respuesta no se ve afectada.

```http
200 OK
{
  "content": [
    { "id": "0193...", "sku": "MED-0001", "name": "Acetaminofén 500 mg x 20", "description": "...", "price": 3.50, "stock": 120 }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```
- `400` si `page < 0`, `size ∉ [1,100]` o `q` es demasiado largo.

## Carrito

### Ver carrito
```http
GET /api/v1/me/cart
200 OK
{
  "items": [
    { "productId": "0193...", "name": "Acetaminofén 500 mg x 20", "unitPrice": 3.50, "quantity": 2, "subtotal": 7.00 }
  ],
  "total": 7.00
}
```

### Agregar producto
```http
POST /api/v1/me/cart/items
{ "productId": "0193...", "quantity": 2 }
```
- Si el producto ya está en el carrito, se **suma** la cantidad. Responde 200 con el carrito actualizado.
- `400` si la cantidad no está en `[1, 99]`.
- `404 PRODUCT_NOT_FOUND`
- `409 INSUFFICIENT_STOCK`

### Quitar producto
`DELETE /api/v1/me/cart/items/{productId}` → 200 con el carrito actualizado · `404 CART_ITEM_NOT_FOUND`

## Pedidos

### Crear pedido (checkout)
```http
POST /api/v1/orders
Authorization: Bearer <jwt>

{ "cardToken": "tok_Vx2kq9...", "deliveryAddress": "Av. Principal 123, Caracas" }
```
`deliveryAddress` es opcional: si no viene, se usa la dirección del cliente.

Flujo:
1. Valida que el carrito no esté vacío y que la tarjeta pertenezca al cliente.
2. Reserva el stock.
3. Intenta el pago hasta `1 + app.payment.max-retries` veces.
4. Envía el email de forma asíncrona.

```http
201 Created
Location: /api/v1/orders/0193c...

{
  "id": "0193c...",
  "status": "PAID",
  "total": 7.00,
  "deliveryAddress": "Av. Principal 123, Caracas",
  "card": { "last4": "1111", "brand": "VISA" },
  "items": [ { "productId": "0193...", "name": "Acetaminofén 500 mg x 20", "unitPrice": 3.50, "quantity": 2 } ],
  "paymentAttempts": [
    { "attempt": 1, "result": "REJECTED", "at": "2026-11-06T15:10:00.100Z" },
    { "attempt": 2, "result": "APPROVED", "at": "2026-11-06T15:10:00.320Z" }
  ],
  "transactionId": "5f0c...",
  "createdAt": "2026-11-06T15:10:00Z"
}
```
- Si se agotan los intentos, la respuesta sigue siendo `201`, pero con `"status": "PAYMENT_FAILED"`: el pedido **se creó** y queda registrado. Además se libera el stock, el carrito se conserva y se envía el email de fallo.
- `400 EMPTY_CART`
- `404 CARD_NOT_FOUND`: el token no existe o pertenece a otro cliente.
- `409 INSUFFICIENT_STOCK`

### Consultar pedido
`GET /api/v1/orders/{orderId}` → 200 (mismo cuerpo) · `404 ORDER_NOT_FOUND` si no existe o no es del cliente.

## Auditoría
```http
GET /api/v1/audit?transactionId=5f0c...
X-API-Key: <llave>

200 OK
[
  { "eventType": "ORDER_CREATED",    "entityType": "ORDER", "entityId": "0193c...", "outcome": "SUCCESS", "createdAt": "..." },
  { "eventType": "PAYMENT_ATTEMPT",  "entityType": "ORDER", "entityId": "0193c...", "outcome": "FAILURE", "details": { "attempt": 1 }, "createdAt": "..." },
  { "eventType": "PAYMENT_ATTEMPT",  "entityType": "ORDER", "entityId": "0193c...", "outcome": "SUCCESS", "details": { "attempt": 2 }, "createdAt": "..." },
  { "eventType": "PAYMENT_SUCCEEDED","entityType": "ORDER", "entityId": "0193c...", "outcome": "SUCCESS", "createdAt": "..." },
  { "eventType": "EMAIL_SENT",       "entityType": "ORDER", "entityId": "0193c...", "outcome": "SUCCESS", "createdAt": "..." }
]
```

---

## Formato de error
```http
400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://api.tokenization-system.dev/errors/invalid-card",
  "title": "Invalid card data",
  "status": 400,
  "detail": "The card data failed validation",
  "code": "INVALID_CARD",
  "transactionId": "5f0c...",
  "errors": [
    { "field": "cardNumber", "message": "fails Luhn check" },
    { "field": "expirationYear", "message": "card is expired" }
  ]
}
```

## Catálogo de errores

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Body o parámetros inválidos (Bean Validation) |
| 400 | `MALFORMED_REQUEST` | JSON mal formado o tipos incorrectos |
| 400 | `INVALID_CARD` | Luhn, expiración, CVV o marca no soportada |
| 400 | `EMPTY_CART` | Checkout con el carrito vacío |
| 401 | `INVALID_API_KEY` | Falta `X-API-Key` o es incorrecta |
| 401 | `UNAUTHORIZED` | Falta el JWT, o es inválido o expiró |
| 401 | `INVALID_CREDENTIALS` | Login fallido |
| 404 | `CUSTOMER_NOT_FOUND`, `PRODUCT_NOT_FOUND`, `CARD_NOT_FOUND`, `ORDER_NOT_FOUND`, `CART_ITEM_NOT_FOUND` | El recurso no existe o no pertenece al cliente |
| 409 | `EMAIL_ALREADY_EXISTS`, `PHONE_ALREADY_EXISTS` | Datos duplicados al registrar |
| 409 | `INSUFFICIENT_STOCK` | No hay stock suficiente (al agregar al carrito o en el checkout) |
| 409 | `CONCURRENT_MODIFICATION` | Conflicto de bloqueo optimista que no se resolvió tras reintentar |
| 422 | `TOKENIZATION_REJECTED` | Rechazo simulado de la tokenización |
| 500 | `INTERNAL_ERROR` | Error inesperado (mensaje genérico; el detalle solo va al log con el `transactionId`) |

> El 404 en lugar de 403 para recursos de otro cliente es intencional: no revela que el recurso existe.
