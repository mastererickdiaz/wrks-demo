# Tasks: Pedidos con Múltiples Productos

**Input**: Design documents from `/specs/003-pedidos-multiples-productos/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/orders-items-api.md, quickstart.md

**Tests**: Obligatorias por el Principio IV (Disciplina de Pruebas,
NON-NEGOTIABLE). El Constitution Check de `plan.md` exige además **cero
regresiones** en los tests ya existentes de `order-service` (specs 001/002).

**Organización**: US1 = Crear pedido multi-producto, US2 = Consultar pedido
con detalle. Ambas son P1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Sin dependencias nuevas que agregar (confirmado en plan.md).

- [X] T001 Confirmar que `order-service/pom.xml` no requiere cambios (Jakarta Bean Validation ya presente vía `spring-boot-starter-validation`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Nuevos records de modelo/DTO y modificación de los existentes
(`Order`, `OrderRequest`). Bloquea el inicio de US1 y US2.

- [X] T002 [P] Crear record `OrderLineItem` (`productName`, `quantity`, `unitPrice`) con método `subtotal()` en `order-service/src/main/java/com/example/orderservice/model/OrderLineItem.java`
- [X] T003 [P] Crear record `OrderLineItemRequest` (`productName` `@NotBlank`, `quantity` `@Min(1)`, `unitPrice` `@NotNull @Min(0)`) en `order-service/src/main/java/com/example/orderservice/dto/OrderLineItemRequest.java`
- [X] T004 Modificar `Order` (record): reemplazar `productName`/`quantity`/`price` por `items: List<OrderLineItem>` y `totalPrice: BigDecimal` en `order-service/src/main/java/com/example/orderservice/model/Order.java` (depende de T002)
- [X] T005 Modificar `OrderRequest` (record): reemplazar `productName`/`quantity`/`price` por `items: List<OrderLineItemRequest>` con `@NotEmpty @Valid` en `order-service/src/main/java/com/example/orderservice/dto/OrderRequest.java` (depende de T003)

**Checkpoint**: Modelos y DTOs listos; el código existente que los usa
(`OrderService`) aún no compila — se corrige en US1.

---

## Phase 3: User Story 1 - Crear un pedido con varios productos (Priority: P1) 🎯 MVP

**Goal**: `POST /api/orders` acepta una lista de líneas, calcula el total en
el servidor, y rechaza atómicamente listas vacías o líneas inválidas.

**Independent Test**: Pasos 2-4 de `quickstart.md`.

### Tests for User Story 1 ⚠️ (obligatorias, Principio IV — escribir y verificar que FALLAN antes de implementar)

- [X] T006 [P] [US1] Test de pedido con 3 líneas: el total calculado es la suma de `cantidad × precio unitario` de cada línea, en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T007 [P] [US1] Test de pedido con 1 sola línea (caso mínimo válido) en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T008 [P] [US1] Test de que el orden de las líneas enviado se preserva en la respuesta en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T009 [P] [US1] Test (vía `@WebMvcTest`) de que `POST /api/orders` con `items: []` responde `400` en `order-service/src/test/java/com/example/orderservice/controller/OrderControllerTest.java` (archivo nuevo)
- [X] T010 [P] [US1] Test (vía `@WebMvcTest`) de que `POST /api/orders` con una línea de `quantity: 0` responde `400` y no se invoca `OrderService.createOrder` en `order-service/src/test/java/com/example/orderservice/controller/OrderControllerTest.java`
- [X] T011 [P] [US1] Test de regresión: `userId` inexistente/inactivo sigue rechazando con 404/400 (FR-011, spec 001) usando el nuevo formato de `OrderRequest` con `items` en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Modificar `OrderService.createOrder`: construir `items` desde `request.items()`, calcular `totalPrice` como suma de `subtotal()`, y usarlos al construir el `Order` (depende de T004, T005)
- [X] T013 [US1] Modificar los 3 overloads de `OrderService.createOrderFallback` para usar `request.items()`/total calculado en vez de `productName`/`quantity`/`price` (depende de T012)
- [X] T014 [US1] Ejecutar T006-T011 y confirmar que todos pasan en verde

**Checkpoint**: US1 funcional; pedidos multi-producto se crean con
validación atómica y total calculado por el servidor.

---

## Phase 4: User Story 2 - Consultar un pedido con el detalle de sus productos (Priority: P1)

**Goal**: `GET /api/orders/{id}` y `GET /api/orders` exponen `items` y
`totalPrice` completos, sin mezclar datos entre pedidos.

**Independent Test**: Paso 2 de `quickstart.md` (la respuesta de creación
ya incluye el detalle) y una consulta posterior por id y en listado.

### Tests for User Story 2 ⚠️ (obligatorias, Principio IV)

- [X] T015 [P] [US2] Test de que `OrderService.getOrderById` devuelve el pedido con todas sus líneas y el total correcto en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T016 [P] [US2] Test de que `OrderService.getAllOrders` devuelve múltiples pedidos, cada uno con sus propias líneas y total, sin mezclarse en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`

### Implementation for User Story 2

- [X] T017 [US2] Confirmar que `OrderController.getOrderById`/`getAllOrders` no requieren cambios de código (ya devuelven `Order` genéricamente; la nueva forma se expone automáticamente) — revisar y documentar la confirmación
- [X] T018 [US2] Ejecutar T015-T016 y confirmar que todos pasan en verde

**Checkpoint**: US2 funcional; el detalle multi-producto es consultable
individual y en listado.

---

## Phase 5: Migración de regresiones (requerida por el Constitution Check)

**Purpose**: El cambio de contrato de `OrderRequest`/`Order` rompe construcciones
existentes en los tests de las specs 001/002. Esta fase es obligatoria antes
de considerar la feature completa (no es opcional ni "deuda técnica nueva").

- [X] T019 Actualizar todas las construcciones de `OrderRequest` y `Order.builder()...` en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java` (tests de transición de estado, historial y notificación de la spec 002) para usar `items`/`totalPrice` en vez de `productName`/`quantity`/`price`
- [X] T020 Ejecutar la suite completa de `order-service` (`mvn test`) y confirmar 0 fallos, 0 errores — ninguna regresión sobre specs 001/002

**Checkpoint**: Toda la suite de `order-service` (specs 001, 002 y 003) pasa
en verde simultáneamente.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T021 Ejecutar `quickstart.md` completo (pasos 1-5) contra el stack levantado con `docker compose up --build`, incluyendo el paso 5 que confirma que historial/transición (spec 002) siguen intactos
- [X] T022 Actualizar `order-service/README.md` con el nuevo formato de `POST /api/orders` (`items` en vez de campos planos) y referencia a `contracts/orders-items-api.md`
- [X] T023 Revisar logs de creación de pedido para confirmar que siguen siendo informativos con la nueva forma (p. ej. incluir cantidad de líneas y total en el log existente), por el Principio V

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias
- **Foundational (Phase 2)**: depende de Setup; BLOQUEA el inicio de US1 y US2
- **US1 (Phase 3)**: depende de Foundational completa
- **US2 (Phase 4)**: depende de US1 (T012), porque la consulta expone lo que US1 construye
- **Migración (Phase 5)**: depende de US1 y US2 completas (necesita la forma final de `Order`/`OrderRequest`)
- **Polish (Phase 6)**: depende de que Migración esté completa (0 regresiones) antes de validar end-to-end

### Parallel Opportunities

- T002-T003 en paralelo (Foundational, archivos distintos)
- T006-T011 en paralelo (tests de US1, aunque varios caen en el mismo archivo `OrderServiceTest.java` — coordinar si se trabaja en paralelo real)
- T015-T016 en paralelo (tests de US2)

---

## Implementation Strategy

### MVP de esta feature

1. Completar Phase 1 (Setup, trivial) y Phase 2 (Foundational)
2. Completar Phase 3 (US1) — sin esto no hay pedidos multi-producto que consultar
3. **STOP y VALIDAR**: pasos 2-4 de `quickstart.md`
4. Completar Phase 4 (US2) → consulta con detalle completo
5. Completar Phase 5 (Migración) — obligatoria, no opcional
6. Completar Phase 6 (Polish)

### Entrega incremental

1. Setup + Foundational → base lista (el build queda roto temporalmente, esperado)
2. US1 → pedidos multi-producto creables, con total correcto
3. US2 → consulta con detalle completo
4. Migración → se restaura la compilación y cobertura de specs 001/002
5. Polish → evidencia de validación end-to-end y documentación
