# Implementation Plan: Plataforma de Gestión de Usuarios y Pedidos (sistema existente)

**Branch**: `001-plataforma-pedidos-usuarios` | **Date**: 2026-06-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-plataforma-pedidos-usuarios/spec.md`

## Summary

La mayor parte de esta feature (FR-001 a FR-007, FR-009, FR-010) ya está implementada
en el código actual: CRUD de usuarios (`user-service`), creación/consulta de pedidos
(`order-service`), descubrimiento vía Eureka, configuración centralizada en
`config-server` + Vault, y enrutamiento único con resiliencia en `api-gateway`. El
trabajo técnico real de este plan se concentra en cerrar dos brechas identificadas en
la spec frente al comportamiento actual:

1. **FR-011** — `order-service` debe rechazar la creación de un pedido cuando el
   `userId` no existe o está inactivo en `user-service`, en vez de crear el pedido
   igualmente (comportamiento actual en `OrderService.createOrder`, que solo registra
   un `log.warn` y continúa).
2. **FR-008 vs. FR-011** — separar la causa "usuario inexistente/inactivo" (rechazo de
   negocio, FR-011) de la causa "user-service caído" (degradación de infraestructura,
   FR-008); hoy ambas se resuelven con el mismo fallback de "usuario dummy"
   (`UserClientService.getUserFallback`), lo que las hace indistinguibles.

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 3.2.0, Spring Cloud 2023.0.0 (OpenFeign, Eureka
Client, Vault Config), Resilience4j 2.1.0 (Circuit Breaker, Retry), Lombok

**Storage**: `order-service` usa una lista en memoria (`List<Order>` + `AtomicLong`),
sin base de datos persistente — limitación conocida y fuera de alcance de este plan.
`user-service` usa H2 vía Spring Data JPA (sin cambios en este plan).

**Testing**: JUnit 5 + Mockito (a introducir; hoy `src/test` está vacío en ambos
servicios — ver Principio IV de la constitución). Para esta feature se requieren
pruebas unitarias de `OrderService.createOrder` cubriendo los tres casos: usuario
válido y activo, usuario inexistente (404), usuario inactivo (400/409), y
`user-service` caído (fallback de infraestructura).

**Target Platform**: Contenedores Docker (`eclipse-temurin:17-jre-alpine`) orquestados
por `docker-compose.yml`, red `microservices-network`.

**Project Type**: Backend de microservicios (múltiples proyectos Maven independientes,
sin frontend).

**Performance Goals**: No hay objetivo numérico definido por el negocio; se mantiene el
comportamiento actual de Resilience4j (reintentos + circuit breaker ya configurados en
`application.yml` de `order-service`) sin degradar la latencia perceptible de
`POST /api/orders`.

**Constraints**: La nueva validación de `userId` se debe resolver mediante la llamada
Feign ya existente (`UserServiceClient.getUserById`); no se introduce un nuevo cliente
HTTP ni una dependencia nueva.

**Scale/Scope**: Cambio acotado a `order-service` (capa `service`, `controller` y
manejo de excepciones); no requiere cambios en `user-service`, `api-gateway`,
`discovery-server`, `config-server` ni `vault`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Arquitectura de Microservicios con Descubrimiento | ✅ PASA — la validación reutiliza la resolución por nombre lógico (`user-service`) vía Eureka/Feign ya existente; no se introducen URLs fijas. |
| II. Configuración Centralizada y Gestión de Secretos | ✅ PASA — no se añaden secretos ni configuración nueva fuera de `config-server`/Vault. |
| III. Resiliencia ante Fallos por Defecto | ✅ PASA con matiz — se mantiene Circuit Breaker/Retry para fallos de infraestructura (FR-008); se añade manejo explícito para el caso "usuario no encontrado/inactivo" (FR-011), que es un rechazo de negocio y NO debe pasar por el fallback de infraestructura. |
| IV. Disciplina de Pruebas (NON-NEGOTIABLE) | ⚠️ GATE — esta feature NO puede marcarse como completa sin las pruebas unitarias descritas en "Testing" arriba. Las tareas generadas por `/speckit-tasks` MUST incluirlas. |
| V. Observabilidad y Trazabilidad | ✅ PASA — se reutiliza `@Slf4j`; se añade un log distinto (nivel `WARN`/`ERROR` según el caso) para diferenciar rechazo de negocio de fallo de infraestructura. |

**Resultado**: GATE condicionado al cumplimiento del Principio IV durante `/speckit-tasks`
y `/speckit-implement`. No hay violaciones que requieran justificación en Complexity
Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-plataforma-pedidos-usuarios/
├── plan.md              # Este archivo
├── data-model.md        # Fase 1: entidades User y Order
├── quickstart.md        # Fase 1: guía de validación end-to-end
├── contracts/
│   └── orders-api.md    # Fase 1: contrato de POST /api/orders (incl. nuevos errores)
└── tasks.md             # Fase 2 (/speckit-tasks) — aún no generado
```

### Source Code (repository root)

```text
order-service/
└── src/main/java/com/example/orderservice/
    ├── controller/
    │   └── OrderController.java        # sin cambios de firma; ajusta manejo de errores
    ├── service/
    │   ├── OrderService.java           # MODIFICA: createOrder valida usuario antes de crear
    │   └── UserClientService.java      # MODIFICA: distingue "no encontrado" de "inactivo" de "infraestructura caída"
    ├── client/
    │   └── UserServiceClient.java      # sin cambios (Feign ya existente)
    ├── exception/                      # NUEVO paquete
    │   ├── UserNotFoundException.java  # NUEVO: mapea a 404
    │   ├── UserInactiveException.java  # NUEVO: mapea a 400/409
    │   └── GlobalExceptionHandler.java # NUEVO: @ControllerAdvice centraliza la traducción a HTTP
    └── model/
        └── User.java                   # sin cambios

order-service/src/test/java/com/example/orderservice/
└── service/
    └── OrderServiceTest.java           # NUEVO: cubre los 4 escenarios de Testing arriba
```

**Structure Decision**: Se mantiene la estructura Maven estándar por servicio ya
presente en el repositorio (`controller` / `service` / `client` / `model`); se añade
únicamente el paquete `exception` en `order-service` para centralizar el mapeo de
errores de negocio a códigos HTTP, evitando lógica de traducción dispersa en el
controlador.

## Complexity Tracking

> Sin violaciones de la constitución que requieran justificación; tabla no aplica.
