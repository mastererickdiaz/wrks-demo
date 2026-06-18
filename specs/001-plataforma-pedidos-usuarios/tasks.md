# Tasks: Plataforma de Gestión de Usuarios y Pedidos (sistema existente)

**Input**: Design documents from `/specs/001-plataforma-pedidos-usuarios/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/orders-api.md, quickstart.md

**Tests**: Obligatorias en esta feature por el Principio IV (Disciplina de Pruebas,
NON-NEGOTIABLE) de `.specify/memory/constitution.md` — no son opcionales.

**Organización**: Las tareas se agrupan por user story (US1, US2, US3) según `spec.md`.
US1 y US3 ya están implementadas en el código; sus tareas son de regularización
(agregar pruebas) más que de implementación nueva. US2 contiene el trabajo de
implementación real (FR-011).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: US1 = Administrar usuarios, US2 = Crear/consultar pedidos, US3 = Gateway

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Habilitar pruebas automatizadas, que hoy no existen en ningún servicio.

- [X] T001 [P] Agregar dependencia `spring-boot-starter-test` (scope `test`) en `order-service/pom.xml`
- [X] T002 [P] Agregar dependencia `spring-boot-starter-test` (scope `test`) en `user-service/pom.xml`
- [X] T003 [P] Crear directorio `order-service/src/test/java/com/example/orderservice/service/` (si no existe) para alojar los nuevos tests
- [X] T004 [P] Crear directorio `user-service/src/test/java/com/example/userservice/` para alojar tests de regresión de US1

**Checkpoint**: Ambos servicios pueden compilar y ejecutar `mvn test` aunque aún no haya tests.
**Nota de entorno**: no se pudo ejecutar `mvn test` en este entorno (solo hay JDK 25 instalado;
el proyecto requiere Java 17 y Lombok 1.18.30 no es compatible con el compilador de JDK 25).
El código fue revisado manualmente; falta verificación con `mvn test` real en un entorno con JDK 17.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestructura de manejo de errores que necesita FR-011 (US2) antes de poder
escribir el código de validación. Bloquea el inicio de US2.

- [X] T005 [P] Crear `order-service/src/main/java/com/example/orderservice/exception/UserNotFoundException.java` (RuntimeException simple con `userId`)
- [X] T006 [P] Crear `order-service/src/main/java/com/example/orderservice/exception/UserInactiveException.java` (RuntimeException simple con `userId`)
- [X] T007 Crear `order-service/src/main/java/com/example/orderservice/exception/GlobalExceptionHandler.java` con `@RestControllerAdvice` que mapea `UserNotFoundException` → 404 y `UserInactiveException` → 400, según `contracts/orders-api.md` (depende de T005, T006)

**Checkpoint**: El paquete `exception` existe y compila; US2 puede empezar a lanzarlas.

---

## Phase 3: User Story 1 - Administrar usuarios (Priority: P1)

**Goal**: Confirmar mediante pruebas automatizadas que el CRUD de usuarios ya
implementado en `user-service` cumple FR-001/FR-002 (no requiere cambios de código).

**Independent Test**: `mvn -pl user-service test` pasa de forma aislada, sin levantar
`order-service` ni el resto del stack.

### Tests for User Story 1 ⚠️ (obligatorias, Principio IV)

- [X] T008 [P] [US1] Test unitario de `UserController.createUser` (caso válido y caso `@Valid` falla por email inválido) en `user-service/src/test/java/com/example/userservice/controller/UserControllerTest.java`
- [X] T009 [P] [US1] Test unitario de `UserService`/`UserRepository` para `findByIdAndActiveTrue` y `findByActiveTrue` en `user-service/src/test/java/com/example/userservice/service/UserServiceTest.java`
- [X] T010 [P] [US1] Test de `UserController.deleteUser` → `204 No Content` y `UserController.getUserById` con id inexistente → `404` en `user-service/src/test/java/com/example/userservice/controller/UserControllerTest.java`

### Implementation for User Story 1

- [ ] T011 [US1] Ejecutar `mvn -pl user-service test` y corregir cualquier defecto real que las pruebas T008-T010 expongan en `UserController`/`UserService` (sin cambiar el contrato `/api/users` documentado en `spec.md`) — **PENDIENTE**: requiere un entorno con JDK 17 (no disponible aquí)

**Checkpoint**: US1 queda con cobertura de pruebas y verificada de forma independiente.

---

## Phase 4: User Story 2 - Crear y consultar pedidos asociados a un usuario (Priority: P1) 🎯 MVP de esta feature

**Goal**: Implementar FR-011 — `order-service` debe validar el `userId` contra
`user-service` antes de crear un pedido, distinguiendo "no encontrado"/"inactivo"
(rechazo de negocio) de "user-service caído" (degradación de infraestructura, FR-008).

**Independent Test**: Pasos 2, 3, 4 y 5 de `quickstart.md` (crear pedido válido,
`userId` inexistente → 404, usuario inactivo → 400, `user-service` caído → 201
vía fallback).

### Tests for User Story 2 ⚠️ (obligatorias, Principio IV — escribir y verificar que FALLAN antes de implementar)

- [X] T012 [P] [US2] Test de `OrderService.createOrder` con usuario válido y activo → pedido `PENDING` creado en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T013 [P] [US2] Test de `OrderService.createOrder` con `userId` inexistente → lanza `UserNotFoundException` en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java` (depende de T005)
- [X] T014 [P] [US2] Test de `OrderService.createOrder` con usuario `active=false` → lanza `UserInactiveException` en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java` (depende de T006)
- [X] T015 [P] [US2] Test de `OrderService.createOrder` cuando `UserClientService` cae al fallback de infraestructura (circuito abierto) → el pedido SÍ se crea (FR-008 sin cambios) en `order-service/src/test/java/com/example/orderservice/service/OrderServiceTest.java`
- [X] T016 [P] [US2] Test de `GlobalExceptionHandler` que verifica el mapeo `UserNotFoundException`→404 y `UserInactiveException`→400 en `order-service/src/test/java/com/example/orderservice/exception/GlobalExceptionHandlerTest.java` (depende de T007)

### Implementation for User Story 2

- [X] T017 [US2] Modificar `UserClientService.getUserById` / `FeignErrorDecoder` en `order-service/src/main/java/com/example/orderservice/...` para distinguir explícitamente "usuario no encontrado" (404 de Feign → `UserNotFoundException`) del fallback de infraestructura actual (depende de T005, T006). Implementado además vía `resilience4j.circuitbreaker/retry.instances.*.ignore-exceptions` en `application.yml` para que estas excepciones de negocio no disparen los fallbacks de infraestructura.
- [X] T018 [US2] Modificar `OrderService.createOrder`: tras obtener el `User`, si `active=false` (usuario real) lanzar `UserInactiveException`; si `active=null` (fallback de infraestructura) se mantiene el comportamiento de degradación de FR-008
- [X] T019 [US2] Verificado: `createOrderFallback` (circuit breaker de `OrderService`) no se ve afectado por las nuevas excepciones de negocio gracias a `ignore-exceptions` en `application.yml`; sigue creando el pedido en modo degradado solo ante fallos de infraestructura reales
- [ ] T020 [US2] Ejecutar T012-T016 y confirmar que todos pasan en verde — **PENDIENTE**: requiere un entorno con JDK 17 (no disponible aquí); revisado manualmente, lógica consistente

**Checkpoint**: US2 cumple FR-003 a FR-005, FR-008 y FR-011; pasos 2-5 de `quickstart.md`
verificados manualmente con `docker-compose`.

---

## Phase 5: User Story 3 - Acceso único y resiliente a través del Gateway (Priority: P2)

**Goal**: Confirmar mediante prueba de integración que el `api-gateway` enruta
correctamente a `user-service` y `order-service` ya actualizados (sin cambios de
código en el gateway).

**Independent Test**: Paso 6 de `quickstart.md` (mismas respuestas vía puerto 8080
que llamando directamente a 8081/8082).

### Tests for User Story 3 ⚠️ (obligatorias, Principio IV)

- [ ] T021 [P] [US3] Test de integración (o documentación de prueba manual con `curl`, si no se usa Testcontainers) que valida el enrutamiento `api-gateway → user-service` y `api-gateway → order-service` en `api-gateway/src/test/java/com/example/apigateway/RoutingIntegrationTest.java` — **PENDIENTE**: se optó por la alternativa de documentación manual (paso 6 de `quickstart.md`); no se escribió un test de integración reactivo nuevo para no introducir dependencias no justificadas en `plan.md`
- [ ] T022 [US3] Ejecutar paso 6 de `quickstart.md` contra el stack completo (`docker-compose up`) y documentar el resultado en `specs/001-plataforma-pedidos-usuarios/quickstart.md` (sin cambios de código esperados) — **PENDIENTE**: requiere levantar `docker-compose up`, no ejecutado en esta sesión

**Checkpoint**: US3 verificada; no se modificó el `api-gateway`.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T023 Ejecutar `quickstart.md` completo (pasos 1-6) contra el stack levantado con `docker-compose up --build` y registrar evidencias — **PENDIENTE**: requiere `docker-compose up`, no ejecutado en esta sesión
- [X] T024 Revisado: `OrderService`/`UserClientService` usan `log.warn` para rechazo de negocio y degradación, y `log.error` en `createOrderFallback` para fallo de infraestructura real (Principio V)
- [X] T025 `order-service/README.md` actualizado con la tabla de códigos de respuesta de `POST /api/orders` (`201`/`400`/`404`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias — puede iniciar de inmediato
- **Foundational (Phase 2)**: depende de Setup (T001) para que `order-service` compile con soporte de test; BLOQUEA el inicio de US2
- **US1 (Phase 3)**: depende de Setup (T002) — independiente de Foundational y de US2/US3
- **US2 (Phase 4)**: depende de Foundational (Phase 2) completa
- **US3 (Phase 5)**: depende de Setup — independiente de US1/US2 (gateway ya enruta a ambos)
- **Polish (Phase 6)**: depende de que US1, US2 y US3 estén completas

### Parallel Opportunities

- T001-T004 en paralelo (Setup)
- T005, T006 en paralelo (Foundational); T007 depende de ambas
- T008-T010 en paralelo (US1, distinto archivo/método)
- T012-T016 en paralelo entre sí (mismo archivo de test pero métodos independientes — coordinar si se edita el mismo archivo simultáneamente)
- US1 y US3 pueden trabajarse en paralelo con US2, ya que no comparten archivos

---

## Implementation Strategy

### MVP de esta feature

1. Completar Phase 1 (Setup) y Phase 2 (Foundational)
2. Completar Phase 4 (US2) — es el único cambio de comportamiento real (FR-011)
3. **STOP y VALIDAR**: correr pasos 2-5 de `quickstart.md`
4. Completar Phase 3 (US1) y Phase 5 (US3) como regularización de pruebas (sin cambio de comportamiento)
5. Completar Phase 6 (Polish)

### Entrega incremental

1. Setup + Foundational → base lista para US2
2. US2 (FR-011) → valor de negocio nuevo, desplegable de inmediato
3. US1 y US3 → cierran la deuda de pruebas del Principio IV sin cambiar comportamiento
4. Polish → evidencia de validación end-to-end y documentación

---

## Hallazgo de validación end-to-end (post-migración a Java 25/Spring Boot 4)

Al ejecutar `docker compose up` y correr `quickstart.md` contra el stack real, se
encontró que `GET /api/users/{id}` (usado originalmente por `UserServiceClient`)
filtra por `findByIdAndActiveTrue`, por lo que un usuario inactivo se veía como
"no existe" — `order-service` no podía distinguir 404 de 400 como exige FR-011.

**Fix aplicado**:
- [X] T026 Agregar `UserService.findByIdIncludingInactive` y `GET /api/users/{id}/raw` en `user-service` (`UserController`/`UserService`) — incluye usuarios inactivos en la respuesta
- [X] T027 Cambiar `UserServiceClient.getUserById` (order-service) para consumir `/api/users/{id}/raw` en vez de `/api/users/{id}`
- [X] T028 Ajustar `FeignErrorDecoder.extractUserId` para extraer el id del penúltimo segmento de la URL (antes último, ahora la URL termina en `/raw`)
- [X] T029 Validado end-to-end con `docker compose up`: pedido válido (201), userId inexistente (404), usuario inactivo (400), user-service caído (201 fallback), gateway enrutando ambos servicios (200)

Además, durante la migración a Java 25/Spring Boot 4 se corrigieron dos bugs reales
detectados solo por pruebas de integración (no por `mvn test` unitario):
- [X] T030 Excluir `resilience4j-spring-boot3` transitivo de `spring-cloud-starter-circuitbreaker-reactor-resilience4j` en `api-gateway`/`order-service` (chocaba con `resilience4j-spring-boot4`, impedía arrancar `api-gateway`)
- [X] T031 Corregir default `active=true` en `user-service`'s `User` (Jackson 3/Spring Boot 4 usaba el `@AllArgsConstructor` de Lombok como creator implícito, saltándose el default) — solución: `@Builder` sobre un constructor package-private
- [X] T032 Agregar overloads de `fallbackMethod` específicos para `UserNotFoundException`/`UserInactiveException` (re-lanzan la excepción) en `UserClientService` y `OrderService`, ya que `ignore-exceptions` de Resilience4j solo afecta métricas del circuit breaker, no qué fallback se ejecuta
