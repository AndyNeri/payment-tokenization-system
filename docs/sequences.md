# Flujos principales

## 1. Tokenización de tarjeta

```mermaid
sequenceDiagram
    autonumber
    actor S as Sistema cliente
    participant F as ApiKeyFilter
    participant C as TokenController
    participant U as TokenizeCardService
    participant R as RejectionPolicy
    participant E as AesGcmCardEncryptor
    participant V as CardVault (JPA)
    participant A as Audit (evento)

    S->>F: POST /api/v1/tokens (X-API-Key, datos de tarjeta)
    alt API Key inválida
        F-->>S: 401 INVALID_API_KEY
    end
    F->>C: request autenticado (+ transactionId en MDC)
    C->>C: Bean Validation
    C->>U: tokenize(command)
    U->>U: Luhn · expiración · CVV · marca
    alt datos inválidos
        U-->>S: 400 INVALID_CARD
    end
    U->>R: shouldReject()?  r < p
    alt rechazada
        U->>A: TokenizationRejected
        U-->>S: 422 TOKENIZATION_REJECTED
    end
    U->>U: token = tok_ + SecureRandom(24B)
    U->>E: encrypt(pan, exp, holder, AAD = token)
    E-->>U: iv + ciphertext + tag
    U->>V: save(token, ciphertext, iv, keyId, last4, brand)
    Note over U,V: El CVV se descarta aquí, nunca se persiste
    U->>A: CardTokenized
    U-->>S: 201 {token, last4, brand}
```

## 2. Registro y login

```mermaid
sequenceDiagram
    autonumber
    actor Cl as Cliente
    participant Cu as CustomerController
    participant RS as RegisterCustomerService
    participant DB as PostgreSQL
    participant Au as AuthController
    participant J as JwtEncoder

    Cl->>Cu: POST /api/v1/customers
    Cu->>RS: register(cmd)
    RS->>DB: existsByEmail / existsByPhone
    alt duplicado
        RS-->>Cl: 409 EMAIL/PHONE_ALREADY_EXISTS
    end
    RS->>DB: INSERT (password = BCrypt)
    Note over RS,DB: El UNIQUE de la BD cubre registros simultáneos (→ 409)
    RS-->>Cl: 201 Customer

    Cl->>Au: POST /api/v1/auth/login
    Au->>DB: findByEmail + BCrypt.matches
    alt credenciales inválidas
        Au-->>Cl: 401 INVALID_CREDENTIALS
    end
    Au->>J: encode(sub = customerId, exp = 30m)
    Au-->>Cl: 200 {accessToken}
```

## 3. Búsqueda de productos con log asíncrono

```mermaid
sequenceDiagram
    autonumber
    actor Cl as Cliente
    participant P as ProductController
    participant S as SearchProductsService
    participant DB as PostgreSQL
    participant EV as Event Publication Registry
    participant L as SearchLogListener (async)

    Cl->>P: GET /api/v1/products?q=... (JWT)
    P->>S: search(q, page, customerId)
    S->>DB: SELECT ... WHERE name ILIKE :q AND stock > :minStock
    S->>EV: publish ProductsSearched(q, count, customerId, txId)
    S-->>Cl: 200 Page<Product>
    EV-)L: después del commit, en un hilo virtual
    L->>DB: INSERT search_logs
    L->>EV: marcar evento completado
    Note over EV,L: Si falla, el evento queda incompleto y se reintenta
```

## 4. Checkout: pago con reintentos y notificación

```mermaid
sequenceDiagram
    autonumber
    actor Cl as Cliente
    participant O as OrderController
    participant U as PlaceOrderService
    participant Ca as Cart API
    participant T as Tokenization API
    participant St as Stock (product)
    participant G as SimulatedPaymentGateway
    participant DB as PostgreSQL
    participant N as NotificationListener (async)
    participant M as SMTP

    Cl->>O: POST /api/v1/orders {cardToken, deliveryAddress} (JWT)
    O->>U: placeOrder(customerId, cmd)
    U->>Ca: getCart(customerId)
    alt carrito vacío
        U-->>Cl: 400 EMPTY_CART
    end
    U->>T: findCard(token, customerId)
    alt no existe o es de otro cliente
        U-->>Cl: 404 CARD_NOT_FOUND
    end
    U->>St: reserve(items)  [UPDATE atómico condicional]
    alt sin stock
        U-->>Cl: 409 INSUFFICIENT_STOCK
    end
    U->>DB: INSERT order (PENDING_PAYMENT)

    loop intento = 1 .. 1 + maxRetries
        U->>G: charge(card, total)
        G->>G: r < p ? REJECTED : APPROVED
        U->>DB: INSERT payment_attempt + audit
        opt APPROVED
            Note over U: sale del loop
        end
        Note over U: backoff antes del siguiente intento
    end

    alt aprobado
        U->>DB: order = PAID · vaciar carrito
        U->>DB: publish PaymentSucceeded (outbox)
    else todos los intentos fallaron
        U->>St: release(items)
        U->>DB: order = PAYMENT_FAILED
        U->>DB: publish PaymentFailed (outbox)
    end
    U-->>Cl: 201 {status, paymentAttempts[]}

    DB-)N: evento (async, después del commit)
    N->>M: SMTP 587 STARTTLS — email de éxito o fallo
    N->>DB: audit EMAIL_SENT / EMAIL_FAILED
```

### Transacciones del checkout
- **T1:** reservar stock + crear la orden `PENDING_PAYMENT`. El commit ocurre antes de cobrar, así la orden existe aunque la app se caiga a mitad del pago.
- **T2 (por intento):** guardar `payment_attempt`. Cada intento queda registrado aunque los siguientes fallen.
- **T3:** estado final + vaciar el carrito o liberar el stock + publicar el evento (que se guarda en `event_publication` en la misma transacción, patrón outbox).
- La reserva de stock es un `UPDATE ... WHERE stock >= :q` por producto, dentro de T1: si un producto no alcanza, toda la reserva hace rollback y se responde `409 INSUFFICIENT_STOCK`.
