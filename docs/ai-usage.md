# Uso de Inteligencia Artificial

Este documento responde al punto 6 de la entrega ("Uso de AI"). Registra qué herramientas se usaron, cómo se configuraron y qué prompts se usaron.

## Herramientas

| Herramienta | Modelo | Uso |
|---|---|---|
| Claude Code (CLI, macOS) | Claude Opus 5.5 | Análisis del enunciado, decisiones de arquitectura, documentación de diseño, generación y revisión de código, pruebas |

### Configuración del agente
- Se ejecuta en el directorio del proyecto, con el modo de permisos por defecto (cada acción con efectos se aprueba).
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
| 2 | "Veo bien Java 21 y Maven + Postgres… ¿Por qué propones usar Spring 3 en vez de 4?"                                                                                       | Se corrigió a **Spring Boot 4.1** (verificado en Maven Central) con justificación |
| 3 | "Instala Temurin 25, usamos mi cuenta personal de GCP y mi GitHub personal."                                                                                              | Se instaló JDK 25 sin sudo, con el checksum verificado |
| 4 | "¿Qué piensas de implementar una arquitectura hexagonal con vertical slicing?"                                                                                            | Hexagonal + slices por feature, con aplicación pragmática → [ADR-001](decisions.md) |
| 5 | "Explícame a qué se refiere con 'probabilidad de rechazo para la creación de tokens'… ¿Cuál es el orden de los pasos? ¿Puedes hacer el diagrama en Excalidraw o draw.io?" | Semántica `r < p` como supuesto ([ADR-007](decisions.md)); orden de trabajo; draw.io + Mermaid |
| 6 | "Realicemos el diseño." + respuestas: API Key + JWT, SMTP Resend/SendGrid, checkout síncrono, repo privado                                                                | Este conjunto de documentos en `docs/` |

### Decisiones en las que el desarrollador corrigió o redirigió a la IA
- Pidió justificar **Spring Boot 3 vs 4**: la IA había propuesto 3 por costumbre y se cambió a 4.1.
- Eligió **API Key + JWT** en lugar de la recomendación inicial (solo API Key).
- Eligió **Resend/SendGrid** en lugar de Gmail SMTP.
- Definió que **git lo maneja el desarrollador**.

## Criterio de uso
Todo lo que generó la IA fue revisado por el desarrollador, que puede explicar cada decisión (ver [decisions.md](decisions.md)). La IA se usó para acelerar, no para reemplazar el criterio técnico.

<!-- Agregar aquí las sesiones siguientes: esqueleto, slices, pruebas, despliegue -->
