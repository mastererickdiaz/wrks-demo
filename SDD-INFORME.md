# Spec-Driven Development (SDD) — Informe de Proceso

> Informe de cómo se aplicó SDD en este repositorio (`wrks-demo`), qué hace
> cada paso del flujo, y cómo se interactúa con él. Usa como caso de estudio
> las tres features ya implementadas: `001-plataforma-pedidos-usuarios`,
> `002-historial-estados-pedido` y `003-pedidos-multiples-productos`.

## 1. ¿Qué es SDD?

**Spec-Driven Development** es una metodología en la que **la especificación
es la fuente de verdad**, no el código. En vez de empezar escribiendo clases
y endpoints, el proceso obliga a decidir y documentar, en este orden:

1. **Qué** principios no negociables rige el proyecto (constitución)
2. **Qué** necesita el negocio, sin mencionar tecnología (especificación)
3. **Cómo** se va a construir técnicamente, validado contra la constitución (plan)
4. **En qué orden** se ejecuta el trabajo, con pruebas obligatorias (tareas)
5. **El código**, generado a partir de todo lo anterior (implementación)

La ventaja frente a "picar código directo": cada decisión queda registrada
y justificada *antes* de escribir una sola línea de Java, lo que permite
detectar ambigüedades, validar reglas de negocio y anticipar conflictos con
features anteriores sin necesidad de refactorizar código ya escrito.

En este repositorio, SDD se implementa con **GitHub Spec Kit**, expuesto
como comandos `/speckit.*` (slash commands) que en Claude Code aparecen como
skills `speckit-*`.

## 2. Estructura de carpetas que genera SDD

```
.specify/
├── memory/
│   └── constitution.md          # Principios del proyecto (vive una sola vez, se actualiza con el tiempo)
├── templates/                    # Plantillas que usan los comandos /speckit.*
└── feature.json                  # Apunta a la feature "activa" en este momento

specs/
├── 001-plataforma-pedidos-usuarios/
│   ├── spec.md                   # QUÉ y POR QUÉ (sin tecnología)
│   ├── checklists/requirements.md
│   ├── plan.md                   # CÓMO (stack técnico + Constitution Check)
│   ├── data-model.md             # Entidades y reglas de datos
│   ├── contracts/                 # Contratos de API (request/response)
│   ├── quickstart.md             # Guía de validación manual (curl) paso a paso
│   └── tasks.md                  # Tareas ejecutables, agrupadas por user story
├── 002-historial-estados-pedido/
│   └── (misma estructura)
└── 003-pedidos-multiples-productos/
    └── (misma estructura)

CLAUDE.md                         # Apunta siempre al plan.md de la feature activa
```

Cada feature es una carpeta numerada (`001`, `002`, `003`, secuencial). Una
vez creada, **nunca se reescribe retroactivamente** — si algo cambia después
(p. ej. un bug encontrado en validación), se documenta como un addendum
dentro de la misma carpeta, no se "corrige la historia".

## 3. El flujo completo, paso a paso

```
/speckit.constitution   →  /speckit.specify  →  /speckit.clarify (opcional)
                                                          ↓
                          /speckit.tasks   ←   /speckit.plan
                                ↓
                      /speckit.implement
                                ↓
                  (validación real: docker compose + curl)
```

### Paso 1 — `/speckit.constitution`

**Qué hace**: crea o actualiza `.specify/memory/constitution.md` con los
principios no negociables del proyecto (arquitectura, testing, seguridad,
observabilidad, etc.), versionado con semver (MAJOR.MINOR.PATCH).

**Cómo interactúo**: lo ejecuto una vez al inicio del proyecto, y lo vuelvo
a tocar solo cuando cambia algo estructural (en este repo: una vez al
crearlo con 5 principios, y una vez más al migrar a Java 25, como cambio de
versión PATCH).

**En este proyecto**: `.specify/memory/constitution.md`, versión `1.0.1`,
con principios sobre arquitectura de microservicios, gestión de secretos,
resiliencia, **disciplina de pruebas (no negociable)**, y observabilidad.

### Paso 2 — `/speckit.specify`

**Qué hace**: a partir de una descripción en lenguaje natural ("quiero que
los pedidos tengan historial de estados..."), genera `specs/NNN-nombre/spec.md`
con:
- User stories priorizadas (P1, P2, P3...), cada una independientemente
  testeable
- Escenarios de aceptación en formato Given/When/Then
- Edge cases
- Requisitos funcionales (`FR-001`, `FR-002`...) testeables
- Criterios de éxito medibles, **sin mencionar tecnología**
- Supuestos (`Assumptions`) para cada decisión sin un dueño claro

Si hay ambigüedades de alto impacto (máximo 3), las marca como
`[NEEDS CLARIFICATION]` y las convierte en preguntas concretas.

**Cómo interactúo**: describo la feature con mis palabras, en términos de
negocio. Si el comando detecta ambigüedades reales, me las presenta como
preguntas con opciones (no como texto libre) para que decida rápido.

**Ejemplos reales de este proyecto**:
- Spec 002 (historial de estados): se preguntó el canal de notificación
  (elegí "evento/webhook") y las reglas de transición de estado (elegí
  "secuencial estricto + cancelación temprana").
- Spec 003 (pedidos multi-producto): no hubo preguntas — todas las
  decisiones (reemplazar el formato anterior, sin límite de líneas) tenían
  un default razonable, documentado en `Assumptions`.

### Paso 3 — `/speckit.clarify` (opcional)

**Qué hace**: si la spec quedó con `[NEEDS CLARIFICATION]` sin resolver,
este paso las convierte en preguntas estructuradas (tabla de opciones) antes
de avanzar a planificación.

**Cómo interactúo**: respondo cada pregunta; el comando actualiza `spec.md`
reemplazando el marcador por la decisión tomada.

*(En este proyecto las clarificaciones se resolvieron directamente durante
`/speckit.specify`, así que no fue necesario un paso separado.)*

### Paso 4 — `/speckit.plan`

**Qué hace**: toma `spec.md` (y la constitución) y genera `plan.md` con:
- **Technical Context**: lenguaje, dependencias, almacenamiento, testing,
  plataforma — decisiones técnicas concretas
- **Constitution Check**: una tabla que evalúa el diseño propuesto contra
  *cada* principio de la constitución, marcando ✅ o ⚠️ (gate condicionado)
- **Estructura del código**: qué archivos se crean/modifican, con razones
- Genera además `data-model.md` (entidades), `contracts/*.md` (contratos de
  API) y `quickstart.md` (guía de validación manual)

**Cómo interactúo**: reviso que el stack y las decisiones técnicas tengan
sentido; si el Constitution Check marca una violación, el plan debe
justificarla en una tabla de "Complexity Tracking" o cambiar de enfoque.

**Ejemplo real**: en la spec 002, el plan documentó *por qué*
`OrderNotificationService` debía ser un bean separado de `OrderService` —
si la notificación se llamara a sí misma dentro de la misma clase, Spring
AOP (y por lo tanto `@CircuitBreaker`/`@Retry`) nunca se activaría en
producción. Esa decisión de diseño quedó en el plan *antes* de escribir
código, no se descubrió debuggeando después.

### Paso 5 — `/speckit.tasks`

**Qué hace**: descompone `plan.md` en una lista de tareas con checkbox
(`tasks.md`), organizadas por:
1. **Setup** — inicialización (dependencias nuevas, si las hay)
2. **Foundational** — modelos/DTOs/excepciones que bloquean todo lo demás
3. **Una fase por user story** (en orden de prioridad), cada una con:
   - Tests obligatorios primero (Principio IV de la constitución)
   - Luego la implementación
4. **Polish** — validación end-to-end, logs, documentación

Cada tarea tiene un ID (`T001`, `T002`...), marcadores `[P]` (paralelizable)
y `[USx]` (a qué historia pertenece), y la ruta exacta del archivo a tocar.

**Cómo interactúo**: reviso el desglose y el orden de dependencias; confirmo
cuál es el "MVP" (normalmente la primera user story) antes de implementar.

### Paso 6 — `/speckit.implement`

**Qué hace**: ejecuta las tareas de `tasks.md` en orden, generando el código
real (modelos, servicios, controladores, excepciones) y sus tests, marcando
cada tarea como `[X]` al completarla.

**Cómo interactúo**: doy el "sí, continúa" inicial; durante la ejecución
reviso los resultados de cada fase (tests en verde antes de avanzar a la
siguiente). Si algo falla en la validación real (ver sección 4), se
corrige ahí mismo y se documenta como un addendum en `tasks.md`, no se
oculta.

### Paso 7 (implícito) — Validación real

**Qué hace**: levantar el sistema de verdad (`docker compose up`) y ejecutar
`quickstart.md` paso a paso contra contenedores reales — no solo `mvn test`.

**Por qué es un paso aparte y no opcional**: en este proyecto, **3 de 3
features** revelaron al menos un problema que los tests unitarios no podían
detectar por sí solos:
- Migración a Java 25: un conflicto de beans de Resilience4j que impedía
  arrancar `api-gateway` por completo.
- Spec 002: el fallback de Resilience4j capturaba excepciones de negocio
  que debían propagarse como 404/400 (`ignore-exceptions` no hace lo que
  parece).
- Spec 002 (hallazgo adicional): `GET /api/users/{id}` excluía usuarios
  inactivos, hacía indistinguible "no existe" de "está inactivo".

Ninguno de estos tres bugs habría aparecido corriendo solo `mvn test` — los
tests unitarios mockean las dependencias, así que los problemas de
*integración real* (arranque de Spring, propagación de excepciones a través
de AOP, contratos entre dos servicios) solo se ven levantando el sistema
completo.

## 4. Cómo se interactúa con SDD (resumen práctico)

| Cuándo | Qué hago yo | Qué hace el asistente |
|---|---|---|
| Inicio del proyecto | Definir principios (o aceptar los inferidos del código) | Genera/actualiza `constitution.md` |
| Nueva feature | Describir la necesidad en lenguaje de negocio | Genera `spec.md`, pregunta solo si hay ambigüedad real (máx. 3 preguntas, con opciones) |
| Tras la spec | Responder las preguntas de clarificación, si las hay | Resuelve los `[NEEDS CLARIFICATION]` en el documento |
| Antes de programar | Revisar el plan técnico y el Constitution Check | Genera `plan.md`, `data-model.md`, contratos, `quickstart.md` |
| Antes de programar | Confirmar el desglose de tareas | Genera `tasks.md` con tests obligatorios primero |
| Implementación | Decir "continúa"; revisar resultados por fase | Escribe código + tests, corre `mvn test`, marca tareas `[X]` |
| Después de implementar | Pedir validación real si quiero confianza total | Levanta `docker compose`, corre `quickstart.md`, reporta status/body de cada paso |
| Si algo falla en validación real | Decidir si se corrige ahora o se documenta para después | Corrige y documenta el hallazgo como addendum en `tasks.md`, nunca lo oculta |

## 5. Las tres features de este proyecto como ejemplo

| Feature | Qué agregó | Bug real encontrado en validación |
|---|---|---|
| `001-plataforma-pedidos-usuarios` | CRUD de usuarios, creación de pedidos, validación cruzada `order-service`→`user-service` | `GET /api/users/{id}` no distinguía "inactivo" de "no existe" → se creó `GET /api/users/{id}/raw` |
| `002-historial-estados-pedido` | Transición de estado con reglas, historial inmutable, notificación vía webhook resiliente | El fallback de Resilience4j enmascaraba excepciones de negocio como infraestructura caída |
| `003-pedidos-multiples-productos` | `Order` pasa de un producto a una lista de líneas + total calculado en servidor | Ninguno — validación end-to-end pasó a la primera (el Constitution Check ya había anticipado la migración de tests rotos) |

## 6. Por qué este proceso, en una frase

SDD cambia el orden en que aparecen los errores: en vez de descubrir
ambigüedades de negocio mientras programas (caro, genera retrabajo) o
descubrir conflictos de integración en producción (muy caro), SDD los
empuja hacia atrás — a la conversación de la spec, a la tabla del
Constitution Check, y a la validación end-to-end *antes* de cerrar la
feature como terminada.
