# CLAUDE.md

Guía para agentes de IA (Claude Code u otros) que trabajen en este repositorio.

## Proyecto
Reto técnico de backend para Farmatodo: tokenización de tarjetas, clientes, productos, carrito, pedidos con pago simulado (N reintentos), emails y auditoría con UUID.
Antes de cambiar algo, lee primero `docs/architecture.md`, `docs/decisions.md` (ADRs) y `docs/api.md`.

## Stack
- Java **25** (Temurin) · Spring Boot **4.1.x** (Spring Framework 7, Jackson 3 → paquete `tools.jackson`)
- Maven Wrapper (`./mvnw`) · PostgreSQL 18 · Flyway · Spring Data JPA (Hibernate 7) · Spring Modulith 2.x
- Pruebas: JUnit 6, Mockito, AssertJ, Testcontainers 2, ArchUnit, JaCoCo (≥ 80%)

## Comandos
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # el JDK por defecto de la máquina no es el 25
./mvnw verify                 # build + pruebas + cobertura + reglas de arquitectura
./mvnw test -Dtest=ClassName  # una clase de prueba
docker compose up --build     # app + postgres + mailpit (UI correos: http://localhost:8025)
```

## Arquitectura (reglas obligatorias)
- Monolito modular **hexagonal** con **vertical slicing por feature**. Paquete base `com.farmatodo.tokenization`.
- Slices: `shared`, `auth`, `tokenization`, `customer`, `product`, `cart`, `order`, `notification`, `audit`.
- Cada slice tiene `domain/` → `application/` → `infrastructure/{in/web, out/<tecnología>}`.
- `domain` **no importa** Spring, JPA ni `infrastructure` (ArchUnit lo verifica).
- Un slice **no** usa el repositorio ni el dominio interno de otro: usa su API pública (`application`) o eventos de dominio (`@ApplicationModuleListener`). Spring Modulith lo verifica.
- Hexagonal **completa** en `tokenization` y `order` (dominio puro + entidades JPA separadas en `infrastructure/out/persistence`). **Ligera** en `product`, `cart`, `audit`, `auth`. No agregues abstracciones sin un segundo uso real.
- El esquema lo crea **Flyway** (`src/main/resources/db/migration`, `V<n>__descripcion.sql`). Hibernate usa `ddl-auto=validate`. **Nunca** edites una migración ya aplicada: crea una nueva.
- `spring.jpa.open-in-view=false`. Las entidades JPA nunca salen por la API: usa DTOs `record`.

## Seguridad (no negociable)
- **Nunca** persistir ni loguear el **CVV**. Nunca loguear el PAN completo. Los DTOs de tarjeta enmascaran en `toString()`.
- Datos de tarjeta: **AES-256-GCM**, IV de 12 bytes con `SecureRandom` por registro, el token como AAD y `key_id` para rotación.
- Tokens y IVs con `SecureRandom`. La aleatoriedad de negocio (probabilidades) va por el puerto `RandomProvider`, para que las pruebas sean deterministas.
- **Sin secretos en el código ni en `application.yml`**: solo `${ENV_VAR}`. Los valores locales van en `.env` (ignorado) y se documentan en `.env.example`.
- La API Key se compara con `MessageDigest.isEqual`. Los recursos del cliente van por rutas `/me/**` (el id sale del JWT).

## Convenciones
- Código y nombres en **inglés**; documentación en `docs/` y mensajes de commit en **español**.
- Errores: excepciones de dominio (`DomainException` y subclases) → `GlobalExceptionHandler` (`@RestControllerAdvice`) → **ProblemDetail RFC 9457** con `code` y `transactionId`. No devolver stacktraces.
- Configuración con `@ConfigurationProperties` + `@Validated` (fail-fast). Ver la tabla de propiedades en `docs/architecture.md` § 6.
- Cada request lleva un `transactionId` (header `X-Transaction-Id`) en el MDC, en la respuesta y en `audit_events`.
- Probabilidades: rechazar si `r < p` (ADR-007).

## Pruebas
- Toda funcionalidad nueva incluye sus pruebas en el mismo cambio. La cobertura mínima es 80% y el build falla por debajo.
- Unitarias: dominio y casos de uso sin Spring (mocks de puertos). Integración: `@SpringBootTest` + Testcontainers Postgres.
- Si cambias un endpoint, actualiza `docs/api.md` y la colección de `postman/`.

## Documentación
- Si cambias una decisión de diseño, actualiza o agrega el ADR en `docs/decisions.md`.
- Registra los prompts relevantes de cada sesión en `docs/ai-usage.md` (requisito de la entrega).

## Git
- El desarrollador hace los commits y push. Los agentes **no** ejecutan `git commit`, `git push` ni crean repos: solo sugieren commits (Conventional Commits con descripción en español: `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `ci:`).
