# Implementation Plan: Pedidos con Múltiples Productos

**Branch**: `003-pedidos-multiples-productos` | **Date**: 2026-06-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-pedidos-multiples-productos/spec.md`

## Summary

Reemplazar el modelo actual de `Order` (un solo `productName`/`quantity`/
`price`) por una lista de líneas de producto (`OrderLineItem`) más un total
calculado en el servidor. Es un cambio de ruptura intencional sobre el
contrato de `POST /api/orders` (spec 001), aceptado porque el sistema no
tiene clientes externos reales. Las reglas ya existentes de validación de
usuario (spec 001, FR-011) y de transición de estado/historial/notificación
(spec 002) se preservan sin cambios, operando sobre el pedido completo.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.0.7, Spring Cloud 2025.1.2 —
mismo stack que el resto de `order-service`)

**Primary Dependencies**: Ninguna dependencia nueva. Se reutiliza Jakarta
Bean Validation (`spring-boot-starter-validation`, ya presente) con
validación anidada (`@Valid` sobre la lista de líneas) para rechazar
pedidos inválidos antes de llegar a la lógica de negocio (FR-002, FR-004).

**Storage**: Sin cambios — en memoria, misma limitación conocida que specs
001/002.

**Testing**: JUnit 5 + Mockito, igual que el resto de `order-service`.
Pruebas unitarias para: cálculo del total, rechazo de lista vacía, rechazo
atómico ante una línea inválida, preservación del orden de las líneas, y
que las transiciones de estado / notificación (spec 002) sigan funcionando
sobre el pedido multi-producto sin cambios de comportamiento.

**Target Platform**: Igual que hoy — contenedor Docker en
`microservices-network`, sin cambios de infraestructura.

**Project Type**: Backend de microservicios. Cambio acotado a
`order-service`; no requiere cambios en `user-service`, `api-gateway`,
`discovery-server`, `config-server` ni `vault`.

**Performance Goals**: Sin objetivo numérico nuevo.

**Constraints**: El total del pedido SIEMPRE se calcula en el servidor; el
cliente nunca puede enviarlo ni sobrescribirlo (FR-003).

**Scale/Scope**: Cambio de forma en `Order`/`OrderRequest` y en los puntos
de `OrderService` que los construyen (`createOrder`, `createOrderFallback`,
`transitionStatus`). No afecta `OrderStatusHistoryEntry`, `NotificationEvent`,
ni los endpoints de estado/historial de la spec 002 (siguen operando sobre
el pedido completo, no por línea).

**Impacto de ruptura conocido**: las pruebas existentes que construyen un
`OrderRequest` con el constructor plano anterior (`OrderServiceTest`,
`OrderNotificationServiceTest` si aplica) deben actualizarse al nuevo
constructor con lista de líneas como parte de esta misma feature — no es
deuda técnica nueva, es la migración natural del contrato.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Evaluación |
|---|---|
| I. Arquitectura de Microservicios con Descubrimiento | ✅ PASA — no se agregan llamadas entre servicios; el cambio es interno a `order-service`. |
| II. Configuración Centralizada y Gestión de Secretos | ✅ PASA — no se introduce configuración ni secretos nuevos. |
| III. Resiliencia ante Fallos por Defecto | ✅ PASA — no se modifican las rutas de Circuit Breaker/Retry existentes (`createOrder`, `getUserById`); solo cambia la forma de los datos que transportan. |
| IV. Disciplina de Pruebas (NON-NEGOTIABLE) | ⚠️ GATE — además de los tests nuevos, esta feature MUST actualizar los tests existentes que rompe (ver "Impacto de ruptura conocido"); no se considera completa si algún test de specs 001/002 queda roto. |
| V. Observabilidad y Trazabilidad | ✅ PASA — los logs existentes (`log.info("Creando orden...")`, etc.) se mantienen; se puede enriquecer con el conteo de líneas/total sin romper el formato. |

**Resultado**: GATE condicionado a (a) cobertura de pruebas nuevas y (b)
cero regresiones en los tests ya existentes de `order-service`. Sin
violaciones que requieran justificación en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/003-pedidos-multiples-productos/
├── plan.md              # Este archivo
├── data-model.md        # Fase 1: OrderLineItem, Order (modificado), OrderRequest (modificado)
├── quickstart.md         # Fase 1: guía de validación end-to-end
├── contracts/
│   └── orders-items-api.md   # Fase 1: nuevo contrato de POST/GET /api/orders
└── tasks.md              # Fase 2 (/speckit-tasks) — aún no generado
```

### Source Code (repository root)

```text
order-service/
└── src/main/java/com/example/orderservice/
    ├── model/
    │   ├── Order.java                # MODIFICA: productName/quantity/price -> items (List<OrderLineItem>) + totalPrice
    │   └── OrderLineItem.java        # NUEVO (record): productName, quantity, unitPrice, subtotal() calculado
    ├── dto/
    │   ├── OrderRequest.java         # MODIFICA: productName/quantity/price -> items (List<OrderLineItemRequest>)
    │   └── OrderLineItemRequest.java # NUEVO (record): productName, quantity, unitPrice, con anotaciones de validación
    ├── service/
    │   └── OrderService.java         # MODIFICA: createOrder calcula el total; createOrderFallback y transitionStatus copian items/totalPrice en vez de productName/quantity/price
    └── controller/
        └── OrderController.java      # Sin cambios de firma (ya usa OrderRequest/Order genéricamente)

order-service/src/test/java/com/example/orderservice/
└── service/
    ├── OrderServiceTest.java              # MODIFICA: construir OrderRequest con items; nuevos casos de FR-002/FR-003/FR-004/FR-005
    └── OrderNotificationServiceTest.java   # Sin cambios (no depende de la forma de Order)
```

**Structure Decision**: Se mantiene la separación `model` (dominio interno,
`OrderLineItem`/`Order`) vs `dto` (forma de entrada HTTP,
`OrderLineItemRequest`/`OrderRequest`) ya usada en el proyecto desde la spec
001. No se crea un controlador ni servicio nuevo: es una evolución del
modelo de datos existente, no una capacidad nueva independiente.

## Complexity Tracking

> Sin violaciones de la constitución que requieran justificación; tabla no aplica.
