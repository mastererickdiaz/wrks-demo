# Tasks: Historial de Estados de Pedidos con Notificación al Usuario

**Input**: Design documents from `/specs/002-historial-estados-pedido/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/orders-status-api.md, quickstart.md

**Tests**: Obligatorias por el Principio IV (Disciplina de Pruebas,
NON-NEGOTIABLE) de `.specify/memory/constitution.md` — no son opcionales.

**Organización**: US1 = Avanzar estado, US2 = Consultar historial,
US3 = Notificar al usuario. US1 y US2 son P1; US3 es P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Sin dependencias nuevas que agregar (confirmado en plan.md);
esta fase solo confirma el estado base.

- [X] T001 Confirmar que `order-service/pom.xml` no requiere cambios (RestClient y Resilience4j ya presentes); no se agrega ninguna dependencia nueva

**Checkpoint**: Nada que instalar; se puede pasar directo a Foundational.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Modelos, DTOs, excepciones y configuración que necesitan tanto
US1 como US2 y US3. Bloquea el inicio de todas las user stories.

- [X] T002 [P] Crear record `OrderStatusHistoryEntry` (`orderId`, `fromStatus`, `toStatus`, `changedAt`) en `order-service/src/main/java/com/example/orderservice/model/OrderStatusHistoryEntry.java`
- [X] T003 [P] Crear record `StatusTransitionRequest` (`status`) en `order-service/src/main/java/com/example/orderservice/dto/StatusTransitionRequest.java`
- [X] T004 [P] Crear record `NotificationEvent` (`orderId`, `recipientEmail`, `fromStatus`, `toStatus`, `occurredAt`) en `order-service/src/main/java/com/example/orderservice/dto/NotificationEvent.java`
- [X] T005 [P] Crear `OrderNotFoundException` en `order-service/src/main/java/com/example/orderservice/exception/OrderNotFoundException.java`
- [X] T006 [P] Crear `InvalidOrderTransitionException` en `order-service/src/main/java/com/example/orderservice/exception/InvalidOrderTransitionException.java`
- [X] T007 Agregar handlers para `OrderNotFoundException` (404) e `InvalidOrderTransitionException` (400) en `order-service/src/main/java/com/example/orderservice/exception/GlobalExceptionHandler.java` (depende de T005, T006)
- [X] T008 Agregar `notifications.webhook.enabled` (default `false`), `notifications.webhook.url`, y la instancia Resilience4j `notificationWebhook` (circuit breaker + retry) en `order-service/src/main/resources/application.yml`

**Checkpoint**: Modelos, DTOs, excepciones y configuración listos; las user
stories pueden empezar.

---

## Phase 3: User Story 1 - Avanzar el estado de un pedido (Priority: P1) 🎯 MVP

**Goal**: Implementar `PATCH /api/orders/{id}/status` con las reglas de
transición de FR-002, registrando cada cambio válido en el historial.

**Independent Test**: Pasos 1-2 y la primera parte del paso 4 de
`quickstart.md` (transición válida avanza el estado; salto/retroceso/mismo
estado/transición desde estado terminal se rechazan; pedido inexistente da 404).

### Tests for User Story 1 ⚠️ (obligatorias, Principio IV — escribir y verificar que FALLAN antes de implementar)

- [X] T009 [P] [US1] Test de transición válida (`PENDING→CONFIRMED`, `CONFIRMED→SHIPPED`, `SHIPPED→DELIVERED`, `PENDING→CANCELLED`, `CONFIRMED→CANCELLED`) actualiza el `status` del pedido en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T010 [P] [US1] Test de transición inválida (salto, retroceso, mismo estado, o desde `SHIPPED`/`DELIVERED`/`CANCELLED`) lanza `InvalidOrderTransitionException` en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T011 [P] [US1] Test de transición sobre un `orderId` inexistente lanza `OrderNotFoundException` en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T012 [P] [US1] Test de los handlers nuevos (`OrderNotFoundException`→404, `InvalidOrderTransitionException`→400) en `order-service/src/test/java/com/example/orderservice/exception/GlobalExceptionHandlerTest.java` (depende de T007)

### Implementation for User Story 1

- [X] T013 [US1] Implementar `OrderService.transitionStatus(Long orderId, Order.OrderStatus newStatus)`: valida contra la tabla de transiciones de `data-model.md`, reemplaza el `Order` en la lista interna (es un record inmutable) y registra la entrada de historial (depende de T002, T005, T006)
- [X] T014 [US1] Implementar `PATCH /api/orders/{id}/status` en `order-service/src/main/java/com/example/orderservice/controller/OrderController.java` usando `StatusTransitionRequest` (depende de T003, T013)
- [X] T015 [US1] Ejecutar T009-T012 y confirmar que todos pasan en verde

**Checkpoint**: US1 funcional; pedidos pueden avanzar de estado con
validación de reglas.

---

## Phase 4: User Story 2 - Consultar el historial de un pedido (Priority: P1)

**Goal**: Implementar `GET /api/orders/{id}/history`, devolviendo las
entradas registradas por US1 en orden cronológico.

**Independent Test**: Paso 3 de `quickstart.md` (historial con 3 entradas en
orden tras 3 transiciones; pedido inexistente da 404; pedido sin
transiciones da lista vacía).

### Tests for User Story 2 ⚠️ (obligatorias, Principio IV)

- [X] T016 [P] [US2] Test de historial vacío (`[]`, no error) para un pedido recién creado sin transiciones en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T017 [P] [US2] Test de historial con múltiples transiciones devuelto en orden cronológico ascendente en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java` (depende de T013)
- [X] T018 [P] [US2] Test de historial sobre `orderId` inexistente lanza `OrderNotFoundException` en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`

### Implementation for User Story 2

- [X] T019 [US2] Implementar `OrderService.getHistory(Long orderId)` reutilizando el almacenamiento de historial creado en T013 (depende de T013)
- [X] T020 [US2] Implementar `GET /api/orders/{id}/history` en `OrderController.java` (depende de T019)
- [X] T021 [US2] Ejecutar T016-T018 y confirmar que todos pasan en verde

**Checkpoint**: US2 funcional; el historial generado por US1 es consultable.

---

## Phase 5: User Story 3 - Notificar al usuario cuando cambia el estado (Priority: P2)

**Goal**: Implementar `OrderNotificationService`: publica (o simula) un
webhook tras cada transición válida, sin que un fallo afecte la respuesta
HTTP de la transición (FR-005, FR-006).

**Independent Test**: Paso 5 de `quickstart.md` (con el webhook
deshabilitado por defecto, cada transición válida deja un log de
notificación simulada y ninguna transición falla por eso).

### Tests for User Story 3 ⚠️ (obligatorias, Principio IV — escribir y verificar que FALLAN antes de implementar)

- [X] T022 [P] [US3] Test de `OrderNotificationService.notify(...)` con webhook habilitado y respuesta 2xx: verifica el cuerpo del evento enviado en `order-service/src/test/java/com/example/orderservice/service/OrderNotificationServiceTest.java`
- [X] T023 [P] [US3] Test de `OrderNotificationService.notify(...)` con webhook habilitado pero el destino falla (timeout/5xx): NO lanza excepción, solo registra el fallo en `order-service/src/test/java/com/example/orderservice/service/OrderNotificationServiceTest.java`
- [X] T024 [P] [US3] Test de `OrderNotificationService.notify(...)` con `notifications.webhook.enabled=false`: registra un log de simulación y no intenta la llamada HTTP en `order-service/src/test/java/com/example/orderservice/service/OrderNotificationServiceTest.java`
- [X] T025 [P] [US3] Test de que `OrderService.transitionStatus` invoca la notificación tras una transición válida y que un fallo de notificación NO afecta el valor retornado/la transición ya aplicada en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`

### Implementation for User Story 3

- [X] T026 [US3] Implementar `OrderNotificationService` con `RestClient`, `@CircuitBreaker`/`@Retry` (instancia `notificationWebhook`) y fallback que solo registra el fallo; cuando `notifications.webhook.enabled=false`, solo loggea (depende de T004, T008)
- [X] T027 [US3] Integrar la llamada a `OrderNotificationService` dentro de `OrderService.transitionStatus`, resolviendo el email vía `UserClientService.getUserById`, sin bloquear ni poder fallar la transición ya aplicada (depende de T013, T026)
- [X] T028 [US3] Ejecutar T022-T025 y confirmar que todos pasan en verde

**Checkpoint**: US3 funcional; las transiciones válidas notifican sin
acoplar el resultado de la notificación al de la transición.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T029 Ejecutar `quickstart.md` completo (pasos 1-5) contra el stack levantado con `docker compose up --build` y registrar evidencias
- [X] T030 Revisar logs de transición rechazada (`WARN`), transición aplicada (`INFO`) y notificación (`INFO` éxito/simulada, `WARN`/`ERROR` fallo) para confirmar trazabilidad clara (Principio V)
- [X] T031 Actualizar `order-service/README.md` con los dos endpoints nuevos y sus códigos de respuesta

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup; BLOQUEA el inicio de US1, US2 y US3
- **US1 (Phase 3)**: depende de Foundational completa
- **US2 (Phase 4)**: depende de US1 (T013), porque el historial se genera durante la transición
- **US3 (Phase 5)**: depende de US1 (T013) para integrarse en `transitionStatus`, pero es independiente de US2
- **Polish (Phase 6)**: depende de que US1, US2 y US3 estén completas

### Parallel Opportunities

- T002-T006 en paralelo (Foundational, archivos distintos)
- T009-T012 en paralelo (tests de US1)
- T016-T018 en paralelo (tests de US2)
- T022-T025 en paralelo (tests de US3)
- US2 y US3 pueden implementarse en paralelo una vez completada US1 (no comparten archivos de implementación, aunque ambas tocan `OrderService.java` — coordinar si se trabaja en paralelo real)

---

## Implementation Strategy

### MVP de esta feature

1. Completar Phase 1 (Setup, trivial) y Phase 2 (Foundational)
2. Completar Phase 3 (US1) — sin esto no hay nada que historiar ni notificar
3. **STOP y VALIDAR**: pasos 1-2 y parte de 4 de `quickstart.md`
4. Completar Phase 4 (US2) → historial consultable
5. Completar Phase 5 (US3) → notificación, sin bloquear el flujo de negocio
6. Completar Phase 6 (Polish)

### Entrega incremental

1. Setup + Foundational → base lista
2. US1 → valor de negocio nuevo (transición controlada), desplegable de inmediato
3. US2 → auditoría/soporte sobre lo que ya generó US1
4. US3 → notificación al usuario, sin riesgo de afectar la disponibilidad de US1/US2
5. Polish → evidencia de validación end-to-end y documentación
