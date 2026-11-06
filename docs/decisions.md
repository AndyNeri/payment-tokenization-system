# Registro de decisiones de arquitectura (ADR)

Formato corto: **Contexto → Decisión → Alternativas → Consecuencias**.

---

## ADR-001 · Monolito modular con hexagonal y vertical slicing por feature

**Contexto.** El reto tiene 7 capacidades de negocio, un plazo corto y se evalúa por *"claridad del diseño"* y *"definición de puertos, protocolos y servicios"*.

**Decisión.** Un solo servicio desplegable, dividido en *slices* por feature (`tokenization`, `customer`, `product`, `cart`, `order`, `notification`, `audit`). Cada slice tiene `domain / application / infrastructure`. Los límites se verifican con ArchUnit y Spring Modulith.

**Alternativas.**
- *Microservicios*: más infraestructura (red, contratos, observabilidad distribuida) y consistencia eventual en el checkout, sin que el reto lo necesite.
- *Capas horizontales* (`controller/service/repository`): mezclan features y acoplan el negocio a la infraestructura.

**Consecuencias.**
- (+) Un solo despliegue y transacciones locales.
- (+) El dominio se prueba sin Spring.
- (+) Cada slice se podría extraer a microservicio; `tokenization` es el primer candidato, para aislar el alcance PCI.
- (−) Más archivos por los mapeos. Se mitiga con hexagonal **completa solo en `tokenization` y `order`** y delgada en el resto.

---

## ADR-002 · Java 25 LTS + Spring Boot 4.1

**Contexto.** Proyecto nuevo en noviembre de 2026.

**Decisión.** Java 25 (LTS más reciente) y Spring Boot 4.1.x (Spring Framework 7).

**Razones.**
- Java 25 trae hilos virtuales sin *pinning* en `synchronized` (JEP 491), caché AOT para arrancar más rápido en Cloud Run (JEP 483/514/515), *compact object headers* (JEP 519) y la API KDF (JEP 510).
- La línea Boot 3.x salió del soporte OSS.
- Spring Framework 7 incluye `@Retryable` y `@ConcurrencyLimit` en el núcleo, null-safety con JSpecify y versionado de API.

**Alternativas.** Java 21 (LTS, válida y más conservadora); Java 26 (no LTS, ~6 meses de soporte).

**Consecuencias.** Las herramientas deben estar al día (JaCoCo ≥ 0.8.14, Jackson 3 con paquete `tools.jackson`, Testcontainers 2). Las versiones quedan fijadas en el `pom.xml` y Maven Enforcer exige Java 25.

---

## ADR-003 · PostgreSQL 18 + Flyway

**Decisión.** PostgreSQL en todos los entornos (Testcontainers en las pruebas de integración) y migraciones versionadas con Flyway. H2 no se usa.

**Razones.** Paridad entre entornos (tipos `jsonb`, `bytea`, `UNIQUE`, índices trigram). Cloud SQL lo ofrece administrado. Las migraciones dejan el esquema reproducible desde cero.

**Consecuencias.** Las pruebas de integración necesitan Docker.

---

## ADR-004 · AES-256-GCM para los datos de tarjeta

**Contexto.** *"Decida muy bien el algoritmo de cifrado."*

**Decisión.** AES-256-GCM, con IV aleatorio de 96 bits por registro, tag de 128 bits, el token como AAD y `key_id` para rotar llaves. La llave (256 bits) viene de Secret Manager.

**Razones.**
- Es **cifrado autenticado**: además de confidencialidad da integridad, porque un ciphertext alterado no se descifra.
- Es estándar (NIST SP 800-38D), está soportado de forma nativa por el JDK y tiene aceleración por hardware (AES-NI / ARMv8).

**Alternativas descartadas.**
- *AES-ECB*: los bloques iguales producen el mismo ciphertext y filtran patrones.
- *AES-CBC*: no tiene integridad sin un HMAC aparte y es vulnerable a *padding oracle*.
- *RSA*: lento y pensado para llaves o datos pequeños, no para registros.
- *Hash del PAN*: el espacio de PANs es pequeño y se puede atacar por fuerza bruta. Además, no es reversible, y el pago necesita recuperar la tarjeta.

**Consecuencias.**
- Nunca se reutiliza un IV con la misma llave. Con IVs aleatorios, el límite práctico es ~2³² cifrados por llave, que se resuelve rotando.
- La llave se pierde = los datos se pierden: se respalda en Secret Manager con versiones.
- Mejora futura: *envelope encryption* con Cloud KMS.

---

## ADR-005 · El CVV nunca se persiste

**Decisión.** El CVV se valida en la tokenización y se descarta. No se guarda en la BD, ni en logs, ni en eventos.

**Razones.** PCI-DSS (req. 3.2) prohíbe almacenar el código de verificación después de la autorización, aunque esté cifrado.

**Consecuencias.** El pago simulado se modela como un cargo *card-on-file* (credencial almacenada), que no requiere CVV.

---

## ADR-006 · API Key para tokenización + JWT para clientes

**Decisión.**
- `X-API-Key` para la tokenización y la consulta de auditoría (integración entre sistemas).
- JWT (HS256, 30 min) emitido en `POST /auth/login` para los endpoints del cliente, que usan rutas `/me/**`.

**Razones.** La tokenización es un servicio para *sistemas*, así que el reto pide API Key. Las operaciones del cliente necesitan identidad de usuario, y el JWT da autenticación *stateless* que escala sin sesiones. Las rutas `/me` evitan IDOR.

**Alternativas.**
- *RS256*: tiene sentido cuando otros servicios validan el token sin compartir el secreto. Con un solo servicio, HS256 es suficiente.
- *OAuth2 con IdP externo*: fuera de alcance.

**Consecuencias.** Sin refresh tokens ni revocación (mejora futura). El secreto JWT va en Secret Manager.

---

## ADR-007 · Probabilidades configurables: se rechaza si `r < p`

**Decisión.** `rejection-probability = p ∈ [0,1]`. Se genera `r ∈ [0,1)` y se rechaza si `r < p`. El aleatorio se inyecta por el puerto `RandomProvider`.

**Razones.** El nombre del parámetro coincide con lo que hace (`p = 0.2` → 20% de rechazos). Es equivalente a la redacción del enunciado comparando contra `1 − p`. El puerto permite pruebas deterministas.

**Consecuencias.** Queda documentado como supuesto. Los valores fuera de rango impiden arrancar (fail-fast).

---

## ADR-008 · Checkout síncrono + notificaciones asíncronas

**Decisión.**
- `POST /orders` ejecuta el pago con sus reintentos (backoff configurable) dentro del request y responde el estado final (`PAID` o `PAYMENT_FAILED`) con la lista de intentos.
- El email se envía de forma asíncrona, por evento.

**Razones.** La pasarela es simulada y rápida, así que la latencia está acotada (`(N+1) × backoff`). El cliente recibe el resultado de inmediato y la colección Postman puede verificarlo de forma determinista. El correo (I/O lento y propenso a fallar) no bloquea la respuesta.

**Alternativa.** 202 + procesamiento en segundo plano + polling. Escala mejor con pasarelas lentas; queda como evolución natural (el caso de uso no cambia, solo el adaptador de entrada).

**Consecuencias.** Hay que acotar `max-retries` y `retry-backoff` para no retener hilos. Con hilos virtuales, la espera es barata.

---

## ADR-009 · Eventos confiables con Spring Modulith

**Decisión.** La comunicación asíncrona entre slices (log de búsquedas, emails, auditoría) usa eventos de Spring con `@ApplicationModuleListener` y el **Event Publication Registry** en JDBC.

**Razones.** Un `@Async` simple pierde el trabajo si la app se reinicia o el listener falla. El registro persiste el evento en la misma transacción que lo origina (patrón *outbox*) y permite reintentar los incompletos. Modulith además verifica los límites entre módulos.

**Alternativas.** Pub/Sub o RabbitMQ: más infraestructura sin necesidad, con un solo servicio.

**Consecuencias.** Se agrega una tabla (`event_publication`) y una dependencia. Si se extraen microservicios, los mismos eventos pasan a un broker (Modulith soporta *externalization*).

---

## ADR-010 · Cloud Run + Cloud SQL + Secret Manager, CI/CD con GitHub Actions y WIF

**Decisión.**
- La app corre en Cloud Run (contenedor, HTTPS administrado, escala a cero) contra Cloud SQL PostgreSQL 18.
- Los secretos están en Secret Manager y se inyectan como variables de entorno.
- GitHub Actions se autentica en GCP con **Workload Identity Federation** (OIDC).

**Razones.** Es serverless (no hay servidores que administrar) y el costo es bajo en idle. Se despliega lo mismo que corre en local (Docker). WIF evita guardar llaves JSON de service accounts en GitHub, uno de los riesgos más comunes.

**Alternativas.** GKE (excesivo); Compute Engine (más operación); App Engine (menos portable).

**Consecuencias.** Hay arranques en frío (se mitigan con la caché AOT y `min-instances` opcional). Las conexiones a Cloud SQL se limitan con el tamaño del pool y `max-instances`.

---

## ADR-011 · Descuento de stock con `UPDATE` atómico condicional (reemplaza el bloqueo optimista)

**Contexto.** El diseño inicial usaba bloqueo optimista (`@Version`) para descontar stock: leer, verificar, escribir, y reintentar si hay conflicto.

**Decisión.** Cada línea se descuenta con `UPDATE products SET stock = stock - :q, version = version + 1 WHERE id = :id AND stock >= :q`. Si afecta 0 filas, no hay stock → 409 `INSUFFICIENT_STOCK`. Las líneas se procesan **ordenadas por id** y dentro de la transacción del llamador (`Propagation.MANDATORY`), así la reserva es todo o nada.

**Razones.**
- **Una sola sentencia atómica**: PostgreSQL bloquea la fila durante el UPDATE y reevalúa la condición, así que es imposible vender de más, sin leer antes y sin reintentos.
- El `CHECK (stock >= 0)` de la tabla queda como red de seguridad adicional.
- Con bloqueo optimista, una oferta con mucha demanda generaría muchos conflictos y reintentos; aquí cada comprador recibe la respuesta en un solo viaje a la BD.
- El orden fijo por id evita *deadlocks* cuando dos pedidos comparten productos.

**Evidencia.** Prueba de integración: 20 compras concurrentes sobre un producto con stock 5 → exactamente 5 exitosas y stock final 0.

**Consecuencias.** La columna `version` se sigue incrementando para que cualquier escritura vía JPA detecte cambios concurrentes. El carrito **no reserva** stock: solo lo valida al agregar; la reserva ocurre en el checkout.
