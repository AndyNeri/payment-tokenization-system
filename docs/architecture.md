# Arquitectura

> Documento de diseño del sistema de tokenización de tarjetas, clientes, productos, carrito y pedidos.
> Decisiones detalladas y su justificación: [decisions.md](decisions.md).

## 1. Visión general

Es un **monolito modular** en **Java 25 + Spring Boot 4.1**, organizado con **arquitectura hexagonal (puertos y adaptadores)** y **vertical slicing por feature**. Cada módulo de negocio (*slice*) tiene dominio, casos de uso y adaptadores propios, y solo se comunica con otros módulos por su API pública o por **eventos de dominio**.

```
                  ┌──────────────────────── Spring Boot app ────────────────────────┐
 HTTP/JSON  ──►   │ shared (security · errors · tracing)                             │
 X-API-Key        │ ┌────────────┐ ┌──────────┐ ┌─────────┐ ┌──────┐ ┌─────────────┐ │
 Bearer JWT       │ │tokenization│ │ customer │ │ product │ │ cart │ │ order/payment│ │
                  │ └────────────┘ └──────────┘ └─────────┘ └──────┘ └─────────────┘ │
                  │        │  events ▼              │ events ▼            │ events ▼  │
                  │   ┌───────┐               ┌──────────────┐      ┌──────────────┐  │
                  │   │ audit │               │ search log   │      │ notification │──┼──► SMTP
                  │   └───────┘               └──────────────┘      └──────────────┘  │
                  └──────────────────────────────┬──────────────────────────────────┘
                                                 │ JDBC (TLS)
                                            PostgreSQL 18
```

El diagrama completo, con despliegue, protocolos y puertos, está en [`diagrams/architecture.drawio`](diagrams/architecture.drawio).

## 2. Slices (módulos)

| Slice | Responsabilidad | Hexagonal |
|---|---|---|
| `shared` | Filtro de API Key, configuración JWT, manejo global de errores (ProblemDetail RFC 9457), `transactionId` en MDC, configuración común | — (kernel compartido mínimo) |
| `auth` | Login de clientes → emisión de JWT | Delgada |
| `tokenization` | Validar la tarjeta, aplicar la probabilidad de rechazo, cifrar, guardar y devolver el token | **Completa** |
| `customer` | Registro y validación de unicidad (email, teléfono) | Media |
| `product` | Búsqueda con stock mínimo configurable y registro asíncrono de búsquedas | Delgada |
| `cart` | Carrito por cliente | Delgada |
| `order` | Pedido, pago simulado con probabilidad y N reintentos, reserva de stock | **Completa** |
| `notification` | Escucha eventos de pago y envía el email | Media |
| `audit` | Log centralizado de eventos y transacciones con UUID | Delgada |

### Estructura de paquetes de un slice
```
com.farmatodo.challenge.<slice>
├── domain/                  ← Java puro: entidades, value objects, reglas, puertos de salida
├── application/             ← casos de uso (puertos de entrada) e implementación
└── infrastructure/
    ├── in/web/              ← controllers REST + DTOs (records)
    └── out/<tecnología>/    ← adaptadores: persistence (JPA), crypto, mail, ...
```

### Reglas de dependencia (verificadas en el build)
1. `domain` **no** depende de Spring, JPA ni de `infrastructure` → test **ArchUnit**.
2. `application` depende solo de `domain`.
3. Un slice **no** accede a los repositorios ni al `domain` interno de otro; usa su API pública (`application`) o eventos → **Spring Modulith** `ApplicationModules.of(App.class).verify()`.
4. Entidades JPA ≠ modelo de dominio en los slices con hexagonal completa (mapeo en el adaptador). En los slices delgados se permite la entidad JPA como modelo para evitar boilerplate sin valor.

> Criterio: la hexagonal se aplica completa donde hay lógica de negocio real y dependencias externas intercambiables (cifrado, pasarela, aleatoriedad). Donde no aporta, no se fuerza.

## 3. Puertos y adaptadores

| Slice | Puerto de entrada (caso de uso) | Puerto de salida | Adaptador |
|---|---|---|---|
| tokenization | `TokenizeCardUseCase`, `FindCardUseCase` | `CardVault` | JPA / PostgreSQL |
| | | `CardEncryptor` | `AesGcmCardEncryptor` (AES-256-GCM) |
| | | `TokenGenerator` | `SecureRandomTokenGenerator` |
| | | `RandomProvider` | `JdkRandomProvider` (`RandomGenerator`) / fijo en tests |
| customer | `RegisterCustomerUseCase`, `GetCustomerUseCase` | `CustomerRepository` | JPA |
| | | `PasswordHasher` | BCrypt (Spring Security) |
| auth | `LoginUseCase` | `TokenIssuer` | `JwtEncoder` (Nimbus, HS256) |
| product | `SearchProductsUseCase` | `ProductRepository`, `SearchLogRepository` | JPA |
| cart | `CartUseCase` | `CartRepository`, `ProductCatalog` | JPA / API de `product` |
| order | `PlaceOrderUseCase`, `GetOrderUseCase` | `OrderRepository` | JPA |
| | | `PaymentGateway` | `SimulatedPaymentGateway` (probabilidad) |
| | | `ProductInventory` | API de `product` (`UPDATE` atómico condicional) |
| notification | (listener de eventos) | `EmailSender` | `SmtpEmailSender` (Spring Mail) |
| audit | `AuditLog` (API pública) | `AuditRepository` | JPA |

## 4. Comunicación entre slices

| Productor | Evento | Consumidor | Modo |
|---|---|---|---|
| product | `ProductsSearched` | product (search log) | Asíncrono, transaccional |
| order | `PaymentSucceeded` | notification, audit | Asíncrono |
| order | `PaymentFailed` | notification, audit | Asíncrono |
| tokenization | `CardTokenized` / `TokenizationRejected` | audit | Asíncrono |

- Se usa `@ApplicationModuleListener` de **Spring Modulith**, que combina `@Async`, `@Transactional(REQUIRES_NEW)` y `@TransactionalEventListener(AFTER_COMMIT)`.
- El **Event Publication Registry** (tabla `event_publication`) guarda cada evento antes de entregarlo. Si la app se cae o el SMTP falla, el evento queda *incompleto* y se reintenta al reiniciar o con un job programado. Así **no se pierden** correos ni logs de búsqueda.
- El executor asíncrono usa **hilos virtuales** (`spring.threads.virtual.enabled=true`).

## 5. Seguridad

| Mecanismo | Dónde | Detalle |
|---|---|---|
| **API Key** (`X-API-Key`) | `POST /api/v1/tokens`, `GET /api/v1/audit` | Integraciones *machine-to-machine*. La llave viene de Secret Manager o del entorno y se compara en tiempo constante (`MessageDigest.isEqual`) para evitar ataques de tiempo. |
| **JWT** (Bearer) | Endpoints de cliente (`/me/**`, `/products`, `/orders`) | Spring Security OAuth2 Resource Server. HS256 con secreto de 256 bits, expiración de 30 min. `sub` = customerId. Además de firma, expiración e issuer, se valida que **el cliente siga existiendo** (`CustomerExistsJwtValidator`): un token de un cliente borrado responde 401 de inmediato (revocación). |
| **Rutas `/me`** | carrito, tarjetas, perfil | El id del cliente sale del token y no de la URL → evita IDOR. |
| **BCrypt** | contraseñas | Vía `DelegatingPasswordEncoder` (permite migrar a Argon2). |
| **AES-256-GCM** | datos de tarjeta | Ver abajo. |
| **Sin secretos en el código** | todo | `application.yml` solo con `${ENV_VAR}`; `.env.example` en local; Secret Manager en GCP. |
| **CORS / headers** | global | Sin CORS abierto (no hay frontend); headers de seguridad por defecto de Spring Security. |

### Protección de datos de tarjeta
- **Algoritmo: AES-256-GCM** (cifrado autenticado, AEAD):
  - IV aleatorio de 12 bytes por registro (`SecureRandom`); **nunca se reutiliza** con la misma llave.
  - Tag de autenticación de 128 bits: detecta cualquier alteración del texto cifrado.
  - **AAD = token**: el texto cifrado queda ligado a su registro; no se puede copiar el ciphertext de una fila a otra.
  - Columna `key_id`: permite **rotar llaves** (descifrar con la vieja, cifrar con la nueva).
- **Se cifra** el payload `{pan, expMonth, expYear, holderName}`.
- **Se guarda en claro solo** `last4` y `brand` (para mostrar la tarjeta enmascarada).
- **El CVV se valida y nunca se persiste**, ni cifrado (PCI-DSS req. 3.2). Tampoco se loguea.
- **El token** es `tok_` + 24 bytes `SecureRandom` en base64url. Es opaco y **no se deriva del PAN**, así que no se puede revertir.
- Los DTOs con datos de tarjeta sobrescriben `toString()` para enmascararlos; el logging nunca recibe el PAN.
- Validaciones: Luhn, longitud 13–19, fecha de expiración futura, CVV de 3–4 dígitos, marca detectada por BIN.

## 6. Configuración externalizada

`@ConfigurationProperties` + `@Validated`: si un valor es inválido, **la app no arranca** (fail-fast).

| Propiedad | Variable de entorno | Default | Validación |
|---|---|---|---|
| `app.tokenization.rejection-probability` | `TOKENIZATION_REJECTION_PROBABILITY` | `0.1` | 0 ≤ p ≤ 1 |
| `app.payment.rejection-probability` | `PAYMENT_REJECTION_PROBABILITY` | `0.3` | 0 ≤ p ≤ 1 |
| `app.payment.max-retries` | `PAYMENT_MAX_RETRIES` | `3` | ≥ 0 |
| `app.payment.retry-backoff` | `PAYMENT_RETRY_BACKOFF` | `200ms` | ≥ 0 |
| `app.product.min-stock` | `PRODUCT_MIN_STOCK` | `0` | ≥ 0 |
| `app.security.api-key` | `API_KEY` | — (obligatoria) | no vacía |
| `app.security.jwt.secret` | `JWT_SECRET` | — (obligatoria) | ≥ 32 bytes |
| `app.security.jwt.ttl` | `JWT_TTL` | `30m` | > 0 |
| `app.crypto.keys` / `app.crypto.active-key-id` | `CARD_ENCRYPTION_KEY` (base64, 32 bytes) | — (obligatoria) | 256 bits |
| `app.notification.from` | `MAIL_FROM` | — | email |
| `spring.mail.*` | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Mailpit en local | — |
| `spring.datasource.*` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Postgres local | — |

Perfiles: `local` (docker-compose), `test` (Testcontainers) y `prod` (Cloud Run + Secret Manager montado como variables de entorno).

### Semántica de las probabilidades (supuesto)
Por cada operación se genera `r ∈ [0, 1)` y **se rechaza si `r < p`**. Así `p = 0` nunca rechaza y `p = 1` siempre rechaza. El enunciado dice *"si el valor aleatorio excede el porcentaje configurado, se rechaza"*; eso es equivalente a comparar contra `1 − p`. Se eligió esta forma para que el nombre `rejection-probability` diga exactamente lo que hace.

## 7. Robustez y manejo de errores

- `@RestControllerAdvice` global → respuestas **ProblemDetail (RFC 9457)** con `type`, `title`, `status`, `detail`, `code`, `transactionId` y `errors[]` para validaciones. Nunca se exponen stacktraces.
- Orden de validación: **sintaxis (Bean Validation) → reglas de dominio → reglas de negocio aleatorias**.
- Unicidad: se valida antes de insertar **y además** con constraints `UNIQUE` en la BD. Una carrera entre dos registros simultáneos termina en un 409, nunca en duplicados.
- Catálogo de errores: [api.md § Errores](api.md#catálogo-de-errores).

## 8. Concurrencia y escalabilidad

- **Stateless**: JWT sin sesión en servidor, así que Cloud Run escala horizontalmente sin sticky sessions.
- **Hilos virtuales** (Java 25, sin *pinning* en `synchronized`): conviene porque la carga es casi toda de I/O (BD, SMTP).
- **Stock**: se descuenta con un `UPDATE products SET stock = stock - :q WHERE id = :id AND stock >= :q` atómico (ADR-011). La BD serializa las escrituras sobre la fila: si dos checkouts compiten por la última unidad, uno actualiza 1 fila y el otro 0, y recibe 409 `INSUFFICIENT_STOCK`. Sin reintentos y sin stock negativo. Las filas se actualizan ordenadas por id para evitar deadlocks.
- **Pool de conexiones**: HikariCP con un tamaño acotado. `max_connections` de Cloud SQL ≥ `instancias_max × pool_size`; con eso se elige el `max-instances` de Cloud Run.
- **Trabajo asíncrono** (logs de búsqueda, emails, auditoría) fuera del camino crítico del request.
- Índices en las columnas de búsqueda y en las FK (ver [data-model.md](data-model.md)).

## 9. Observabilidad y trazabilidad

- **`transactionId` (UUID)** por request: se toma del header `X-Transaction-Id` si viene (y es un UUID válido) o se genera. Va al **MDC**, a la respuesta, a cada log y a cada fila de `audit_events`, `search_logs` y `orders`. Los eventos lo propagan a los listeners asíncronos.
- Logs estructurados en JSON (logging estructurado nativo de Spring Boot); en Cloud Run se integran con Cloud Logging.
- Actuator: `health` (liveness/readiness para Cloud Run) e `info`. `/ping` es el endpoint del reto.

## 10. Despliegue

| Entorno | Componentes |
|---|---|
| **Local** | `docker compose up`: `app` (8080) + `postgres:18` (5432) + `mailpit` (SMTP 1025, UI 8025) |
| **GCP** (`tokenization-system`, `us-central1`) | **Cloud Run** (HTTPS, escala a cero) · **Cloud SQL PostgreSQL 18** (conexión por el conector integrado de Cloud Run) · **Secret Manager** (DB, API key, JWT, llave AES, SMTP) · **Artifact Registry** (imágenes) · SMTP externo (**Resend/SendGrid**, 587 STARTTLS) |
| **CI/CD** | GitHub Actions. PR: build, tests, JaCoCo ≥ 80%, ArchUnit/Modulith, Testcontainers, Gitleaks, Trivy. `main`: build de la imagen → push a Artifact Registry → deploy a Cloud Run → **Newman** (colección Postman) contra la URL desplegada. Autenticación con **Workload Identity Federation** (sin llaves JSON). |

Imagen: build multi-stage con `eclipse-temurin:25-jre`, usuario no root y caché AOT para reducir el arranque en frío.

## 11. Supuestos

1. Probabilidades: se rechaza si `r < p` (ver § 6).
2. El CVV no se guarda nunca. El pago simulado funciona como un cargo con credencial almacenada (*card-on-file*), que no requiere CVV.
3. Los productos se cargan con un seed (Flyway) de productos de farmacia; no se pide su CRUD.
4. "`/ping` aplica para todas las APIs": hay un `/ping` global y un `/api/v1/{slice}/ping` por módulo, todos públicos.
5. La tokenización la consume un backend (API Key) que envía el `customerId` dueño de la tarjeta; el pedido verifica que la tarjeta pertenezca al cliente autenticado.
6. El checkout toma todo el carrito del cliente.
7. "Reintentar N veces" = 1 intento inicial + N reintentos.
8. No hay refresh tokens; el JWT dura 30 min (mejora futura).
9. Si Resend se usa sin dominio verificado, solo envía al correo dueño de la cuenta; para la demo se usa ese correo o SendGrid con un remitente verificado.

## 12. Trazabilidad de requisitos

| # | Requisito del reto | Dónde se cubre |
|---|---|---|
| F1 | Tokenización con API Key y probabilidad de rechazo | slice `tokenization`; § 5, § 6; `POST /api/v1/tokens` |
| F2 | `/ping` → `pong` 200 en todas las APIs | `shared`; supuesto 4 |
| F3 | Registro de clientes con datos válidos y únicos | slice `customer`; § 7; `POST /api/v1/customers` |
| F4 | Búsqueda de productos, log asíncrono y stock mínimo configurable | slice `product`; § 4, § 6 |
| F5 | Carrito | slice `cart`; `/api/v1/me/cart` |
| F6 | Pedido con cliente, tarjeta y dirección; pago con probabilidad y N reintentos | slice `order`; [sequences.md](sequences.md) |
| F7 | Email de éxito o fallo | slice `notification`; § 4 |
| F8 | Logs centralizados en BD con UUID | slice `audit`; § 9 |
| F9 | Docker + Docker Compose | § 10 |
| NF | Seguridad (API Key / JWT, cifrado) | § 5; ADR-004, ADR-005 |
| NF | Concurrencia y configuración externa sin secretos | § 6, § 8 |
| NF | Robustez (errores y validaciones) | § 7 |
| NF | Pruebas ≥ 80% + colección Postman | JaCoCo gate en CI; `postman/` |
| NF | Mantenibilidad (modular, documentado) | § 2, § 3; reglas ArchUnit/Modulith |
| NF | DevSecOps: CI, pruebas continuas, CD | § 10 |
