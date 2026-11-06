# Payment Tokenization System

Reto técnico de backend para Farmatodo: tokenización de tarjetas de crédito, gestión de clientes, búsqueda de productos, carrito, pedidos con pagos simulados (con reintentos), notificaciones por correo y auditoría centralizada.

**Stack:** Java 25 · Spring Boot 4.1 · PostgreSQL 18 · Flyway · Spring Modulith · Docker Compose · GitHub Actions · Google Cloud (Cloud Run, Cloud SQL, Secret Manager, Artifact Registry)

> 🚧 Proyecto en construcción. Las secciones marcadas con _(pendiente)_ se completan a medida que avanza la implementación.

## Documentación

| Documento | Contenido |
|---|---|
| [Arquitectura](docs/architecture.md) | Slices, puertos y adaptadores, seguridad, configuración, concurrencia, despliegue, supuestos, trazabilidad de requisitos |
| [Decisiones (ADR)](docs/decisions.md) | Por qué hexagonal, Java 25, AES-256-GCM, API Key + JWT, checkout síncrono, Modulith, Cloud Run |
| [Modelo de datos](docs/data-model.md) | ERD y tablas |
| [API](docs/api.md) | Endpoints, autenticación, ejemplos y catálogo de errores |
| [Flujos](docs/sequences.md) | Diagramas de secuencia: tokenización, login, búsqueda, checkout |
| [Diagrama de arquitectura](docs/diagrams/architecture.drawio) | Componentes, protocolos y servicios externos (abrir en [diagrams.net](https://app.diagrams.net)) |
| [Uso de IA](docs/ai-usage.md) | Herramientas, configuración y prompts usados |

## Descripción del sistema
Monolito modular con arquitectura hexagonal y vertical slicing por feature. Ver [docs/architecture.md](docs/architecture.md).

![Arquitectura](docs/diagrams/architecture.png)

## Ejecutar en local

**Requisitos:** Docker (con Docker Compose). Para compilar o probar fuera de Docker: JDK 25.

```bash
cp .env.example .env
# Completa los secretos en .env (los comandos para generarlos están en el mismo archivo):
#   API_KEY=$(openssl rand -hex 32)   JWT_SECRET=$(openssl rand -base64 32)   CARD_ENCRYPTION_KEY=$(openssl rand -base64 32)

docker compose up --build
curl http://localhost:8080/ping   # → pong
```

| Servicio | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Mailpit (correos enviados) | http://localhost:8025 |
| PostgreSQL | `localhost:5432` (credenciales en `.env`) |

La app no arranca si falta un secreto o si es débil (validación *fail-fast*).

**Desde el IDE, sin `.env`:** ejecuta `TestPaymentTokenizationSystemApplication` (en `src/test`). Levanta un PostgreSQL temporal con Testcontainers y usa el perfil `test`.

## Pruebas
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS: si tu JDK por defecto no es el 25
./mvnw verify
```
`verify` ejecuta:
- Pruebas unitarias.
- Pruebas de integración contra **PostgreSQL real** (Testcontainers; requiere Docker).
- Reglas de arquitectura (**ArchUnit**: el dominio no depende de frameworks; **Spring Modulith**: límites entre módulos).
- El umbral de cobertura **JaCoCo ≥ 80%**: el build falla si no se cumple.

| Reporte | Ruta |
|---|---|
| Cobertura | `target/site/jacoco/index.html` |
| Diagramas de módulos (PlantUML) | `target/spring-modulith-docs/` |

## Colección Postman _(pendiente)_
`postman/` — instrucciones para importarla y ejecutarla con Newman.

## Despliegue en GCP desde cero _(pendiente)_

## CI/CD
**CI** (`.github/workflows/ci.yml`), en cada push y pull request:
1. **Build y pruebas:** `./mvnw verify` (pruebas, reglas de arquitectura y umbral de cobertura). Publica el resumen de cobertura y los reportes como artefactos.
2. **Escaneo de secretos:** Gitleaks sobre todo el historial.
3. **Build de la imagen Docker.**

**CD** _(pendiente)_: despliegue en Cloud Run.
