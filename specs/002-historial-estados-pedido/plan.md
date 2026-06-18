# Implementation Plan: Historial de Estados de Pedidos con Notificación al Usuario

**Branch**: `002-historial-estados-pedido` | **Date**: 2026-06-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-historial-estados-pedido/spec.md`

## Summary

Agregar a `order-service` la capacidad de transicionar el estado de un pedido
(`PENDING→CONFIRMED→SHIPPED→DELIVERED`, con cancelación temprana desde
`PENDING`/`CONFIRMED`), registrar cada transición en un historial inmutable
por pedido, y publicar — de forma asíncrona y best-effort — un evento de
notificación dirigido al email del usuario propietario, resuelto vía el
cliente Feign hacia `user-service` ya existente. Un fallo en la notificación
NUNCA debe revertir ni bloquear la transición ya aplicada (FR-006).

## Technical Context

**Language/Version**: Java 25 (mismo stack que el resto del repo tras la
migración: Spring Boot 4.0.7, Spring Cloud 2025.1.2)

**Primary Dependencies**: Ninguna dependencia nueva. Se reutiliza
`spring-boot-starter-web` (ya presente) para el cliente HTTP saliente
(`RestClient`, incluido en `spring-web` desde Spring Framework 6.1) y
`resilience4j-spring-boot4`/`spring-cloud-starter-circuitbreaker-reactor-resilience4j`
(ya presentes) para proteger la llamada al webhook.

**Storage**: En memoria, dentro de `order-service`, con la misma estrategia
que los pedidos actuales (`List`/`Map` protegidos por la propia gestión de
`OrderService`). Limitación conocida y heredada (ver spec 001); no se
introduce una base de datos nueva para esta feature.

**Testing**: JUnit 5 + Mockito, igual que el resto de `order-service`.
Pruebas unitarias para las reglas de transición (FR-002, FR-007), el
historial (FR-003, FR-004, FR-008) y el publicador de notificaciones
(FR-005, FR-006), incluyendo el caso de fallo del webhook.

**Target Platform**: Igual que hoy — contenedor Docker en
`microservices-network`, sin cambios de infraestructura.

**Project Type**: Backend de microservicios (cambio acotado a
`order-service`; no requiere cambios en `user-service`, `api-gateway`,
`discovery-server`, `config-server` ni `vault`).

**Performance Goals**: Sin objetivo numérico nuevo. La publicación del
evento de notificación MUST ser asíncrona respecto a la respuesta HTTP de la
transición de estado, para que un webhook lento no degrade la latencia
percibida por el cliente (alineado con FR-006).

**Constraints**: La URL del webhook es configuración, no secreto; vive en
`application.yml`/variables de entorno (no en Vault). Si no hay un receptor
de webhook configurado, el publicador debe degradar a un log estructurado
("notificación simulada") en vez de fallar — así el stack sigue siendo
ejecutable localmente sin un receptor externo real.

**Scale/Scope**: Cambio acotado a `order-service`: nuevo endpoint de
transición, nuevo endpoint de historial, nuevo componente de notificación.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Arquitectura de Microservicios con Descubrimiento | ✅ PASA — no se agregan llamadas síncronas nuevas entre servicios; la resolución del email del usuario reutiliza `UserClientService`/Eureka ya existente. |
| II. Configuración Centralizada y Gestión de Secretos | ✅ PASA — la URL del webhook es configuración (no secreto) y vive en `application.yml`/env vars; no se requiere Vault. No se introduce ninguna dependencia nueva que justificar. |
| III. Resiliencia ante Fallos por Defecto | ✅ PASA — la llamada al webhook se protege con Circuit Breaker + Retry (Resilience4j), con un fallback que solo registra el fallo (FR-006); nunca propaga la excepción a la transición de estado. |
| IV. Disciplina de Pruebas (NON-NEGOTIABLE) | ⚠️ GATE — esta feature NO se considera completa sin pruebas unitarias de: reglas de transición válidas/inválidas, historial vacío/poblado, y publicación de notificación con éxito y con fallo simulado. `/speckit-tasks` MUST incluirlas. |
| V. Observabilidad y Trazabilidad | ✅ PASA — cada transición exitosa, cada transición rechazada, y cada intento de notificación (éxito o fallo) se registran con `@Slf4j` en el nivel apropiado (INFO/WARN/ERROR). |

**Resultado**: GATE condicionado al cumplimiento del Principio IV durante
`/speckit-tasks` y `/speckit-implement`, igual que en la feature 001. Sin
violaciones que requieran justificación en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-historial-estados-pedido/
├── plan.md              # Este archivo
├── data-model.md        # Fase 1: OrderStatusHistoryEntry, NotificationEvent
├── quickstart.md         # Fase 1: guía de validación end-to-end
├── contracts/
│   └── orders-status-api.md   # Fase 1: contrato de los 2 endpoints nuevos
└── tasks.md              # Fase 2 (/speckit-tasks) — aún no generado
```

### Source Code (repository root)

```text
order-service/
└── src/main/java/com/example/orderservice/
    ├── controller/
    │   └── OrderController.java          # MODIFICA: agrega PATCH /{id}/status y GET /{id}/history
    ├── service/
    │   ├── OrderService.java             # MODIFICA: lógica de transición + historial; dispara notificación
    │   └── OrderNotificationService.java # NUEVO: construye y publica el evento de notificación (webhook)
    ├── model/
    │   ├── Order.java                     # sin cambios de forma (sigue siendo record); se reemplaza la
    │   │                                   # instancia en la lista interna al cambiar de estado
    │   └── OrderStatusHistoryEntry.java   # NUEVO (record): orderId, fromStatus, toStatus, changedAt
    ├── dto/
    │   ├── StatusTransitionRequest.java   # NUEVO (record): { "status": "CONFIRMED" }
    │   └── NotificationEvent.java         # NUEVO (record): orderId, email, fromStatus, toStatus, timestamp
    └── exception/
        ├── OrderNotFoundException.java        # NUEVO: 404
        ├── InvalidOrderTransitionException.java # NUEVO: 400
        └── GlobalExceptionHandler.java         # MODIFICA: agrega los dos handlers anteriores

order-service/src/main/resources/
└── application.yml       # MODIFICA: agrega notifications.webhook.* y resilience4j para "notificationWebhook"

order-service/src/test/java/com/example/orderservice/
├── service/
│   ├── OrderServiceTest.java               # MODIFICA: agrega casos de transición/historial
│   └── OrderNotificationServiceTest.java    # NUEVO: éxito y fallo del webhook
└── exception/
    └── GlobalExceptionHandlerTest.java      # MODIFICA: agrega los dos handlers nuevos
```

**Structure Decision**: Se mantiene la estructura por capas ya usada en
`order-service` (`controller`/`service`/`model`/`dto`/`exception`). El
historial se modela como un nuevo record (`OrderStatusHistoryEntry`) en el
mismo paquete `model`, y la notificación como un servicio nuevo y separado
(`OrderNotificationService`) para no mezclar la lógica de transición de
estado (negocio) con la lógica de entrega de notificaciones (infraestructura
saliente) — así el fallo de una no puede filtrarse a la otra por accidente,
reforzando FR-006.

## Complexity Tracking

> Sin violaciones de la constitución que requieran justificación; tabla no aplica.
