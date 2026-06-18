# Tasks: Trazabilidad Distribuida de Peticiones

**Input**: Design documents from `/specs/004-trazabilidad-distribuida/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/tracing-config.md, quickstart.md

**Tests**: Esta feature es transversal (sin lógica de negocio nueva), así
que la "prueba" obligatoria por el Principio IV no es JUnit sino la
**validación explícita de FR-005** (caída de Jaeger no afecta el negocio) —
ver Phase 5. No es opcional: es el gate que el Constitution Check de
`plan.md` dejó condicionado.

**Organización**: US1 = Seguir el recorrido completo (P1, MVP), US2 = Medir
tiempos por paso (P2). Ambas se logran con la misma instrumentación base
(Foundational); lo que cambia es qué se valida en `quickstart.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Agregar el starter de OpenTelemetry a los 5 servicios.

- [X] T001 [P] Agregar `spring-boot-starter-opentelemetry` (sin versión explícita) en `api-gateway/pom.xml`
- [X] T002 [P] Agregar `spring-boot-starter-opentelemetry` en `config-server/pom.xml`
- [X] T003 [P] Agregar `spring-boot-starter-opentelemetry` en `discovery-server/pom.xml`
- [X] T004 [P] Agregar `spring-boot-starter-opentelemetry` en `order-service/pom.xml`
- [X] T005 [P] Agregar `spring-boot-starter-opentelemetry` en `user-service/pom.xml`

**Checkpoint**: Los 5 servicios compilan con el starter en el classpath
(sin configuración de exportación todavía — exportarán al endpoint OTLP
por defecto, que no existe aún).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Configuración compartida (sampling, endpoint OTLP) y el nuevo
contenedor `jaeger`. Bloquea el inicio de US1 y US2 (sin esto no hay dónde
ver las trazas).

- [X] T006 Agregar el servicio `jaeger` (`jaegertracing/all-in-one:latest`, puertos `16686`/`4317`/`4318`) a `docker-compose.yml`, en `microservices-network`, **sin** agregarlo a `depends_on` de ningún otro servicio (FR-005: el negocio no debe depender de que Jaeger esté arriba)
- [X] T007 [P] Agregar `management.tracing.sampling.probability: 1.0` en `application.yml` de los 5 servicios (`api-gateway`, `config-server`, `discovery-server`, `order-service`, `user-service`)
- [X] T008 [P] Agregar `management.otlp.tracing.endpoint: http://jaeger:4318/v1/traces` en `application-docker.yml` de los 5 servicios

**Checkpoint**: Al levantar `docker compose up`, los 5 servicios exportan
trazas a Jaeger; `http://localhost:16686` muestra los servicios registrados.

---

## Phase 3: User Story 1 - Seguir el recorrido completo de una petición (Priority: P1) 🎯 MVP

**Goal**: Confirmar que una petición que atraviesa 2+ servicios produce una
traza consultable con la jerarquía completa, sin cruzar logs.

**Independent Test**: Pasos 1, 2 y 4 de `quickstart.md`.

- [X] T009 [US1] Ejecutar pasos 1-2 de `quickstart.md`: crear usuario + pedido, y verificar en Jaeger UI que la traza de `POST /api/orders` muestra el span de `order-service` y el span saliente hacia `user-service` con relación padre-hijo
- [X] T010 [US1] Ejecutar paso 4 de `quickstart.md`: provocar un `404` (userId inexistente) y verificar que el span correspondiente queda marcado como error en Jaeger UI, identificable sin abrir logs

**Checkpoint**: US1 cumplida — SC-001 y SC-002 de la spec verificados
visualmente contra Jaeger real.

---

## Phase 4: User Story 2 - Medir cuánto tiempo tomó cada paso (Priority: P2)

**Goal**: Confirmar que cada span individual expone su propia duración.

**Independent Test**: Paso 3 de `quickstart.md`.

- [X] T011 [US2] Ejecutar paso 3 de `quickstart.md`: expandir los spans de la traza del paso 2 y verificar que cada uno muestra su duración individual, y que la suma de los spans hijos es coherente con la duración del span padre

**Checkpoint**: US2 cumplida — SC-004 de la spec verificado.

---

## Phase 5: Validación de resiliencia (gate obligatorio, Principio IV)

**Purpose**: Esta fase es la que el Constitution Check de `plan.md` dejó
condicionada — no es opcional. Verifica FR-005/SC-003 de forma explícita,
no solo por inspección de código.

- [X] T012 Ejecutar paso 5 de `quickstart.md`: `docker compose stop jaeger`, repetir un `POST /api/orders` válido, confirmar `201` sin degradación perceptible, luego `docker compose start jaeger`
- [X] T013 Repetir la misma prueba de T012 pasando por `api-gateway` (puerto 8080) en vez de directo a `order-service`, para cubrir también la ruta de entrada principal del sistema

**Checkpoint**: Confirmado que ninguna ruta de negocio depende de que
Jaeger esté disponible.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T014 Revisar los logs de arranque de cada uno de los 5 servicios (`docker compose up --build`) para confirmar que la autoconfiguración de OpenTelemetry se activó sin warnings/errores (mismo tipo de verificación que destapó el conflicto de Resilience4j durante la migración a Java 25)
- [X] T015 Documentar en el `README.md` raíz cómo acceder a Jaeger UI (`http://localhost:16686`) y qué servicio buscar para una traza reciente
- [X] T016 Confirmar que las métricas/logs ya existentes (Micrometer/Prometheus, `@Slf4j`) siguen funcionando sin cambios — esta feature se integra con observabilidad existente, no la reemplaza (Principio V)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias — los 5 `pom.xml` son archivos distintos, totalmente paralelo
- **Foundational (Phase 2)**: depende de Setup; BLOQUEA el inicio de US1 y US2
- **US1 (Phase 3)** y **US2 (Phase 4)**: ambas dependen de Foundational; son independientes entre sí (T009-T010 no requieren T011 ni viceversa)
- **Validación de resiliencia (Phase 5)**: depende de Foundational (necesita el contenedor `jaeger` existiendo para poder apagarlo)
- **Polish (Phase 6)**: depende de que las fases 3-5 estén completas

### Parallel Opportunities

- T001-T005 en paralelo (Setup, 5 archivos distintos)
- T007-T008 en paralelo entre sí, y cada uno en paralelo a través de los 5 servicios (10 ediciones de archivo independientes)
- T009-T010 (US1) pueden ejecutarse en paralelo con T011 (US2)

---

## Implementation Strategy

### MVP de esta feature

1. Completar Phase 1 (Setup) y Phase 2 (Foundational) — sin esto no hay nada que ver
2. Completar Phase 3 (US1) — la capacidad central: ver el recorrido completo
3. **STOP y VALIDAR**: confirmar visualmente en Jaeger UI antes de seguir
4. Completar Phase 4 (US2) — duración por paso
5. Completar Phase 5 (Validación de resiliencia) — **obligatoria**, no se considera la feature completa sin esto
6. Completar Phase 6 (Polish)

### Entrega incremental

1. Setup + Foundational → trazas visibles en Jaeger para cualquier petición
2. US1 → diagnóstico de fallas sin cruzar logs (valor inmediato para soporte/operación)
3. US2 → diagnóstico de lentitud
4. Validación de resiliencia → confianza de que esto es observabilidad pura, no una dependencia oculta del negocio
5. Polish → documentación y confirmación de que nada existente se rompió

---

## Hallazgos reales durante la implementación (no anticipados en plan.md)

1. **`management.otlp.metrics.export` ruidoso**: el starter trae también un
   registro OTLP de métricas (no solo trazas) que, sin configuración,
   apuntaba a `localhost:4318` dentro del contenedor y fallaba cada minuto
   con `Failed to publish metrics to OTLP receiver`. Fix:
   `management.otlp.metrics.export.enabled: false` en los 5 servicios — las
   métricas ya van a Prometheus, no se necesitaba un segundo canal.
2. **Propiedad de endpoint deprecada y no funcional**:
   `management.otlp.tracing.endpoint` (el nombre intuitivo) existe en la
   metadata de configuración pero está deprecada con nivel `error` desde
   Spring Boot 4.0.0, **sin relocation automática** — usarla no genera
   ningún error, simplemente no exporta ninguna traza (Jaeger quedaba con
   `0` servicios registrados). La propiedad correcta es
   `management.opentelemetry.tracing.export.otlp.endpoint`, confirmada
   leyendo el campo `deprecation.replacement` del
   `spring-configuration-metadata.json` real del jar.
3. **Feign no propagaba el contexto de traza**: aun con el endpoint
   correcto, `order-service` y `user-service` generaban `traceId` distintos
   para la misma petición. Causa: `spring-cloud-starter-openfeign` no trae
   `feign-micrometer` por defecto, y sin ese artefacto Feign no se
   instrumenta con Micrometer Observation (que es lo que propaga el header
   `traceparent`). Fix: agregar `io.github.openfeign:feign-micrometer`
   (sin versión explícita, gestionada por `feign-bom` vía
   `spring-cloud-openfeign-dependencies`) a `order-service/pom.xml`.
   Verificado en Jaeger: una sola traza con jerarquía de 3 spans
   (`http post /api/orders` → `HTTP GET` saliente → `http get /api/users/{id}/raw`).

Los tres hallazgos quedaron documentados en `contracts/tracing-config.md` y
reflejados en el código; ninguno requirió cambiar el alcance de la spec,
solo corregir la configuración técnica.
