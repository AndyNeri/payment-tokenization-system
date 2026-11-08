# Uso de Inteligencia Artificial

Este documento responde al punto 6 de la entrega ("Uso de AI"). Registra qué herramientas se usaron, cómo se configuraron y qué prompts se usaron.

## Herramientas

| Herramienta | Modelo | Uso |
|---|---|---|
| Claude Code (CLI, macOS) | Claude Opus 5.5 | Análisis del enunciado, decisiones de arquitectura, documentación de diseño, generación y revisión de código, pruebas |

### Configuración del agente
- Se ejecuta en el directorio del proyecto con permisos controlados: las acciones sensibles (permisos IAM, escritura de secretos, hacer pública la API) **no** las ejecuta el agente; las ejecuta el desarrollador después de revisarlas.
- **Regla de trabajo:** el agente **no** hace commits ni push. Propone qué commitear y el desarrollador ejecuta git.
- **[`CLAUDE.md`](../CLAUDE.md)** en la raíz del repo: contexto del proyecto, comandos, reglas de arquitectura y seguridad, y convenciones que el agente debe seguir. Es la "card" de configuración del agente.
- Memoria persistente del proyecto: plazo, stack y reglas de trabajo.
- Instrucciones globales (`AGENTS.md`): reservar el modelo caro para decisiones y revisión, y delegar el trabajo mecánico en subagentes cuando aplique.
- El agente verificó las versiones de librerías directamente en Maven Central, en lugar de asumirlas.

## Bitácora de prompts

### Sesión 1 — Análisis y diseño (2026-11-05 / 06)

| # | Prompt (resumen fiel)                                                                                                                                                     | Resultado |
|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| 1 | "Lee el siguiente proyecto a realizar y ayúdame a entenderlo bien y cuales son los requisitos"                                                                            | Resumen de requisitos, decisiones clave (cifrado, asincronía, reintentos), stack y fases |
| 2 | "Usemos Java 25 y Maven + Postgres… ¿Por qué propones usar Spring 3 en vez de 4?"                                                                                         | Se corrigió a **Spring Boot 4.1** (verificado en Maven Central) con justificación |
| 3 | "Instala Temurin 25, usamos mi cuenta personal de GCP y mi GitHub personal."                                                                                              | Se instaló JDK 25 sin sudo, con el checksum verificado |
| 4 | "¿Qué piensas de implementar una arquitectura hexagonal con vertical slicing?"                                                                                            | Hexagonal + slices por feature, con aplicación pragmática → [ADR-001](decisions.md) |
| 5 | "Explícame a qué se refiere con 'probabilidad de rechazo para la creación de tokens'… ¿Cuál es el orden de los pasos? ¿Puedes hacer el diagrama en Excalidraw o draw.io?" | Semántica `r < p` como supuesto ([ADR-007](decisions.md)); orden de trabajo; draw.io + Mermaid |
| 6 | "Realicemos el diseño." + respuestas: API Key + JWT, SMTP transaccional (Resend/SendGrid), checkout síncrono con correo asíncrono                                         | Este conjunto de documentos en `docs/` |

### Sesión 2 — Implementación por módulos (2026-11-06 / 07)
Cada módulo se pidió en su propia rama (GitHub Flow). El desarrollador revisó, hizo los commits, abrió los PR y los mergeó con el CI en verde.

| #  | Prompt (palabras del desarrollador, ortografía corregida)                                                                        | Resultado |
|----|----------------------------------------------------------------------------------------------------------------------------------|---|
| 7  | "Listo, arranquemos con el esqueleto."                                                                                           | Proyecto base: seguridad, errores RFC 9457, `transactionId`, Docker, CI |
| 8  | "Realicemos el módulo de tokenización segun el diseño."                                                                          | Módulo de tokenización con AES-256-GCM |
| 9  | "¿Ya podemos hacer la Postman collection? Quiero que llevemos git con buenas prácticas: ¿`main` y `develop`? ¿Rama por feature?" | Se adoptó **GitHub Flow** (rama por feature + PR + `main` protegida) |
| 10 | "Creé la rama `feature/customer-auth`: haz cliente y login JWT, después arma la Postman collection."                             | Registro, login JWT y colección Postman + Newman en CI |
| 11 | "Ya lo puse público, aplica las reglas para proteger main."                                                                      | Ruleset que protege `main` con 4 checks obligatorios |
| 12 | "¿Para qué estamos creando otra rama para el Postman?"                                                                           | Se descartó la rama extra; Postman va con su feature |
| 13 | "¿No podemos arreglar que, si el JWT trae un cliente que no existe…? ¿Por qué se daría ese caso?"                                | `CustomerExistsJwtValidator`: un token de un cliente borrado pasa a 401 |
| 14 | "Agrega lo de Postman de una vez, antes de que hagamos el PR."                                                                   | Carpetas de productos y carrito en la colección |
| 15 | "Arranca con pedidos y pagos."                                                                                                   | Checkout con reserva atómica de stock y pago con N reintentos |
| 16 | "Ya hice el merge, arranca." (notificaciones y auditoría)                                                                        | Correos por eventos con reintento automático; auditoría por `transactionId` |

### Sesión 3 — Despliegue en GCP (2026-11-07)
| #  | Prompt (palabras del desarrollador, ortografía corregida)                                                                               | Resultado |
|----|-----------------------------------------------------------------------------------------------------------------------------------------|---|
| 17 | "En GCP yo ya había creado el proyecto `tokenization-system`." (detuvo la ejecución para confirmar qué se iba a crear)                  | Se aclaró: solo un perfil local de gcloud, sin tocar la configuración corporativa |
| 18 | "Pásame todos los comandos y  guarda todas las instrucciones de cómo desplegar desde cero en GCP."                                      | [docs/deploy-gcp.md](deploy-gcp.md) |
| 19 | (El desarrollador ejecutó el script de permisos IAM y secretos) "Ya corrí el script, sigue con el resto, pero explícame cómo lo haces." | Imagen, `gcloud run deploy`, verificación |
| 20 | (El desarrollador ejecutó el permiso para hacer pública la API)                                                                         | API en línea; Newman contra GCP: 48 requests, 195 aserciones |
| 21 | "Ya hice el merge, revisa el CD."                                                                                                       | Primer despliegue continuo verificado (WIF, revisión nueva, smoke test) |
| 22 | "Dale, arranca con el diagrama y el README."                                                                                            | Diagrama y README finales |

## Aportes y criterio del desarrollador
Decisiones tomadas o corregidas por el desarrollador durante el proyecto y su impacto.

| Área | Qué decidió o cuestionó | Impacto |
|---|---|---|
| **Stack** | Pidió justificar Spring Boot 3: la IA lo había propuesto por costumbre. Exigió entender por qué Java 25. | Se usó **Spring Boot 4.1** (la versión vigente), con cada versión verificada en Maven Central |
| **Arquitectura** | Planteó la **arquitectura hexagonal con vertical slicing por feature**. Cuestionó que unos módulos tuvieran hexagonal completa y otros ligera. | Arquitectura modular verificada automáticamente (ArchUnit + Spring Modulith). El rigor se aplica según el riesgo de cada módulo, justificado en [ADR-001](decisions.md) |
| **Seguridad** | Eligió **API Key + JWT** en vez de solo API Key. Cuestionó qué pasa si un JWT válido es de un cliente que ya no existe. | Los clientes se autentican con JWT y los recursos van por `/me/**` (sin IDOR). Un token de un cliente borrado ahora da 401, y se eliminó un caso de mensaje "veneno" en el log de búsquedas |
| **Separación de entornos** | Exigió usar sus cuentas personales de GitHub y GCP, separadas de las corporativas. Detuvo la ejecución en GCP para confirmar qué se iba a crear. | Perfil de gcloud aislado: imposible desplegar por error en un proyecto corporativo |
| **Control de cambios** | Definió que **git lo maneja el desarrollador**: la IA solo sugiere commits. Detectó commits con la identidad equivocada y pidió rehacerlos. Propuso trabajar con buenas prácticas de ramas. | GitHub Flow: una rama por feature, PR y `main` protegida con 4 checks obligatorios. Historial limpio con su identidad |
| **Simplicidad** | Cuestionó una rama extra para Postman y un script generador dentro del repo. | El repo solo contiene lo que se entrega (la colección JSON), sin herramientas auxiliares |
| **Proveedor de correo** | Cuando SendGrid bloqueó la cuenta nueva, cambió a **Brevo** y asumió el compromiso de enviar desde un remitente verificado sin dominio propio. | Cero cambios de código, gracias al adaptador SMTP genérico; los correos llegan en producción |
| **Operación segura** | Ejecutó personalmente, después de revisarlas, las acciones sensibles: permisos IAM, carga de secretos (incluida la SMTP key) y apertura pública de la API. | Ningún secreto pasó por el chat, el código ni un archivo. El agente nunca tuvo permisos para cambiar IAM ni secretos |
| **Documentación** | Pidió documentar cada comando del despliegue "desde cero" y corregir el diagrama donde los textos se encimaban. | [docs/deploy-gcp.md](deploy-gcp.md) reproducible y diagrama legible |
| **Calidad de la entrega** | Pidió las pruebas de API (Postman) antes de cada PR y la verificación de cada CI. | 48 requests y 195 aserciones que corren en cada PR y contra producción |

## Cómo trabajé con la IA

Usé la IA como un par técnico, no como un generador automático. Mi forma de trabajar fue:
1. **Primero entender, después construir.** Antes de escribir código pedí que me explicara el enunciado, las alternativas de arquitectura y el porqué de cada versión del stack. Cuando una propuesta no me convencía (Spring Boot 3, una rama extra, un script dentro del repo), la cuestioné hasta que hubo una justificación o se cambió.
2. **Un módulo por rama.** Yo creaba la rama y definía el alcance. La IA implementaba con pruebas. Yo revisaba, hacía los commits, abría el PR y solo hacía merge con el CI en verde.
3. **Lo sensible lo ejecuto yo.** Permisos en la nube, secretos y la exposición pública de la API los revisé y los ejecuté personalmente.

## Criterio de uso
Todo lo que generó la IA fue revisado por el desarrollador, que puede explicar cada decisión (ver [decisions.md](decisions.md)). La IA se usó para acelerar, no para reemplazar el criterio técnico.
