# Implementation Plan: Trazabilidad Distribuida de Peticiones

**Branch**: `004-trazabilidad-distribuida` | **Date**: 2026-06-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-trazabilidad-distribuida/spec.md`

## Summary

Instrumentar los 5 servicios (`api-gateway`, `discovery-server`,
`config-server`, `user-service`, `order-service`) con **Micrometer Tracing
+ el bridge a OpenTelemetry**, exportando vía **OTLP** a un nuevo contenedor
**Jaeger (`jaegertracing/all-in-one`)**, que lo acepta de forma nativa (sin
necesidad de un colector intermedio). En Spring Boot 4 existe un starter
dedicado (`spring-boot-starter-opentelemetry`) que trae todo lo necesario en
una sola dependencia — verificado contra el POM real en Maven Central, no
asumido de memoria (lección de la migración a Java 25 de este mismo repo).

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.0.7, Spring Cloud 2025.1.2 —
mismo stack que el resto del repo)

**Primary Dependencies**: `org.springframework.boot:spring-boot-starter-opentelemetry`
(versión gestionada por el BOM del padre, 4.0.7). Confirmado en
`spring-boot-starter-opentelemetry-4.0.7.pom` que incluye, sin dependencias
adicionales que declarar a mano:
- `spring-boot-starter-micrometer-metrics`
- `spring-boot-micrometer-tracing-opentelemetry`
- `spring-boot-opentelemetry` (autoconfiguración)
- `micrometer-tracing-bridge-otel`
- `opentelemetry-exporter-otlp`
- `micrometer-registry-otlp`

No se requiere agregar el SDK de OpenTelemetry "a mano" ni un colector
intermedio (Jaeger acepta OTLP directamente en los puertos 4317 gRPC /
4318 HTTP — verificado levantando `jaegertracing/all-in-one:latest` en este
entorno).

**Storage**: Las trazas se almacenan dentro de Jaeger (backend in-memory del
`all-in-one` por defecto, suficiente para el entorno de demo/desarrollo de
este repo). No se introduce una base de datos de trazas separada.

**Testing**: JUnit 5, igual que el resto del repo. Dado que la trazabilidad
es transversal (no es lógica de negocio), las pruebas se enfocan en: (a) que
el contexto de traza se propaga entre servicios (verificable con un test de
integración ligero o inspección manual en Jaeger UI durante `quickstart.md`),
y (b) que una caída del exportador OTLP no rompe ninguna petición de
negocio (FR-005) — se prueba apagando Jaeger y repitiendo el flujo de
`quickstart.md` de la spec 001.

**Target Platform**: Contenedor Docker adicional (`jaeger`) en
`microservices-network`, igual que el resto de la infraestructura
compartida (`vault`, `redis`).

**Project Type**: Cambio transversal de configuración en los 5
microservicios + un servicio nuevo de infraestructura en `docker-compose.yml`.
No se modifica lógica de negocio existente.

**Performance Goals**: Captura completa de trazas (sin sampling agresivo),
acorde a la Assumption de la spec (bajo volumen, entorno de demo).

**Constraints**: La exportación de trazas MUST ser asíncrona/no bloqueante
respecto al procesamiento de la petición (el exportador OTLP de Micrometer
ya opera así por defecto vía un `BatchSpanProcessor`); si Jaeger no está
disponible, las peticiones de negocio MUST seguir respondiendo con
normalidad (FR-005).

**Scale/Scope**: Todos los servicios obtienen el starter y la configuración
de exportación; no se requiere código de aplicación nuevo (controladores,
servicios) — es instrumentación automática vía autoconfiguración de Spring
Boot sobre Tomcat/Netty, `RestClient`/Feign, y JDBC si aplicara.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Arquitectura de Microservicios con Descubrimiento | ✅ PASA — Jaeger se registra como un contenedor de infraestructura más en `docker-compose.yml`, igual que `vault`/`redis`; no participa en Eureka (no es un microservicio de negocio). |
| II. Configuración Centralizada y Gestión de Secretos | ✅ PASA — la URL del endpoint OTLP es configuración (no secreto), vía `application.yml`/env vars, igual que `notifications.webhook.url` de la spec 002. No se requiere Vault. |
| III. Resiliencia ante Fallos por Defecto | ✅ PASA — FR-005/SC-003 exigen explícitamente que una caída del backend de trazas no afecte el negocio; el exportador OTLP de Micrometer ya es asíncrono y "fire-and-forget" por diseño (no se necesita Circuit Breaker manual aquí, a diferencia de spec 002 donde sí se llamaba a un servicio propio). |
| IV. Disciplina de Pruebas (NON-NEGOTIABLE) | ⚠️ GATE — debe verificarse explícitamente (no solo asumirse) que apagar Jaeger no rompe ninguna petición de negocio, como parte de `/speckit-tasks`. |
| V. Observabilidad y Trazabilidad | ✅ PASA — esta feature *es* la mejora de observabilidad; se integra con los logs (`@Slf4j`) y métricas (Micrometer/Prometheus) ya existentes, no los reemplaza. |

**Resultado**: GATE condicionado a probar explícitamente el escenario de
caída de Jaeger (Principio IV). Sin violaciones que requieran justificación
en Complexity Tracking — no se agrega ninguna dependencia fuera de un único
starter oficial de Spring Boot.

## Project Structure

### Documentation (this feature)

```text
specs/004-trazabilidad-distribuida/
├── plan.md              # Este archivo
├── data-model.md         # Fase 1: conceptos Trace/Span mapeados a Micrometer/OTel
├── quickstart.md         # Fase 1: guía de validación end-to-end (incluye caída de Jaeger)
├── contracts/
│   └── tracing-config.md # Fase 1: propiedades de configuración por servicio
└── tasks.md               # Fase 2 (/speckit-tasks) — aún no generado
```

### Source Code (repository root)

```text
docker-compose.yml                     # MODIFICA: nuevo servicio "jaeger" (jaegertracing/all-in-one)

api-gateway/pom.xml                     # MODIFICA: + spring-boot-starter-opentelemetry
config-server/pom.xml                   # MODIFICA: + spring-boot-starter-opentelemetry
discovery-server/pom.xml                # MODIFICA: + spring-boot-starter-opentelemetry
order-service/pom.xml                   # MODIFICA: + spring-boot-starter-opentelemetry
user-service/pom.xml                    # MODIFICA: + spring-boot-starter-opentelemetry

*/src/main/resources/application.yml            # MODIFICA: management.tracing.sampling.probability=1.0
*/src/main/resources/application-docker.yml      # MODIFICA: endpoint OTLP -> http://jaeger:4318
```

**Structure Decision**: No se crea ningún paquete Java nuevo ni controlador
nuevo — toda la instrumentación es autoconfiguración de Spring Boot sobre lo
que ya existe (Tomcat/Netty, Feign, `RestClient`). El único componente de
infraestructura nuevo es el contenedor `jaeger` en `docker-compose.yml`. Es
deliberadamente el cambio menos invasivo posible para una feature
transversal: la lógica de negocio de las specs 001-003 no se toca.

## Complexity Tracking

> Sin violaciones de la constitución que requieran justificación; tabla no aplica.
