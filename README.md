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

## Ejecutar en local _(pendiente)_
```bash
cp .env.example .env
docker compose up --build
curl http://localhost:8080/ping   # → pong
```
- API: http://localhost:8080 · Swagger UI: http://localhost:8080/swagger-ui.html
- Mailpit (correos): http://localhost:8025

## Pruebas _(pendiente)_
```bash
./mvnw verify           # unitarias + integración (Testcontainers) + cobertura JaCoCo ≥ 80%
```
Reporte de cobertura: `target/site/jacoco/index.html`

## Colección Postman _(pendiente)_
`postman/` — instrucciones para importarla y ejecutarla con Newman.

## Despliegue en GCP desde cero _(pendiente)_

## CI/CD _(pendiente)_
