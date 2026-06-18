# Feature Specification: Trazabilidad Distribuida de Peticiones

**Feature Branch**: `004-trazabilidad-distribuida`

**Created**: 2026-06-18

**Status**: Draft

**Input**: Quiero implementar trazabilidad distribuida (observabilidad de
peticiones a través de los microservicios) para poder diagnosticar fallas y
lentitud sin cruzar logs manualmente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Seguir el recorrido completo de una petición (Priority: P1)

Un operador del sistema necesita ver, para una petición específica, el
recorrido completo a través de todos los servicios que la atendieron (por
ejemplo: entrada por el gateway, validación de usuario, creación de
pedido), sin tener que correlacionar manualmente los logs de cada servicio
por hora/timestamp.

**Why this priority**: Es la capacidad central; sin ella no hay
trazabilidad distribuida, solo logs aislados por servicio como hoy.

**Independent Test**: Se puede probar enviando una petición que atraviese
al menos dos servicios (p. ej. crear un pedido, que valida el usuario contra
otro servicio) y verificando que existe un identificador común que permite
recuperar el recorrido completo de esa petición específica.

**Acceptance Scenarios**:

1. **Given** una petición que atraviesa el gateway y dos o más servicios
   internos, **When** la petición se completa (con éxito o con error),
   **Then** un operador puede consultar el recorrido completo de esa
   petición usando un identificador único, viendo cada servicio que
   participó y en qué orden.
2. **Given** una petición que falla en un servicio intermedio, **When** se
   consulta su recorrido, **Then** se identifica claramente en qué servicio
   ocurrió la falla, sin necesidad de revisar los logs de cada servicio por
   separado.
3. **Given** una petición que no llega a involucrar ningún servicio interno
   (por ejemplo, rechazada por validación en el punto de entrada), **When**
   se consulta, **Then** igual existe un registro (aunque corto) de esa
   petición.

---

### User Story 2 - Medir cuánto tiempo tomó cada paso (Priority: P2)

Un operador necesita ver cuánto tiempo se consumió en cada servicio
involucrado en una petición, para detectar cuál de ellos es responsable de
una lentitud general.

**Why this priority**: Aporta valor de diagnóstico de rendimiento, pero el
sistema ya es útil para diagnosticar fallas (US1) sin esto.

**Independent Test**: Se puede probar enviando una petición con múltiples
pasos y verificando que el recorrido consultado en US1 incluye la duración
de cada paso, no solo el orden.

**Acceptance Scenarios**:

1. **Given** una petición que atraviesa varios servicios, **When** se
   consulta su recorrido, **Then** se muestra cuánto tiempo se consumió en
   cada servicio individualmente y cuánto tiempo total tomó la petición de
   extremo a extremo.
2. **Given** dos peticiones equivalentes, una rápida y otra lenta, **When**
   se comparan sus recorridos, **Then** es posible identificar en qué paso
   específico está la diferencia de tiempo.

---

### Edge Cases

- ¿Qué pasa si el sistema de trazabilidad no está disponible (caído o
  inalcanzable)? El tráfico de negocio MUST seguir funcionando con
  normalidad; solo se pierde la visibilidad de ese período, no la
  disponibilidad del sistema.
- ¿Qué pasa con operaciones que continúan después de haber respondido al
  cliente (por ejemplo, el envío de una notificación en segundo plano)?
  MUST poder asociarse al recorrido de la petición que las originó, aunque
  ocurran después de la respuesta HTTP.
- ¿Qué pasa con peticiones hacia servicios de infraestructura de terceros
  (vault, redis, descubrimiento de servicios)? Se traza el lado de la
  llamada que el propio sistema hace hacia ellos cuando sea relevante para
  el diagnóstico, pero no se instrumenta el interior de esos servicios de
  terceros.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST asignar un identificador único de correlación
  a cada petición entrante en el punto de entrada, y propagarlo a través de
  todos los servicios internos que la atiendan.
- **FR-002**: El sistema MUST registrar, para cada servicio que participó
  en una petición, el tiempo consumido y si el paso terminó con éxito o con
  error.
- **FR-003**: El sistema MUST permitir a un operador consultar el recorrido
  completo de una petición específica usando su identificador de
  correlación.
- **FR-004**: El sistema MUST distinguir, dentro del recorrido de una
  petición, el tiempo consumido dentro de cada servicio del tiempo de
  espera de red entre servicios.
- **FR-005**: Una falla o indisponibilidad del sistema de trazabilidad MUST
  NOT bloquear ni degradar el procesamiento normal de peticiones de
  negocio.
- **FR-006**: El sistema MUST asociar operaciones en segundo plano
  originadas por una petición (por ejemplo, notificaciones asíncronas) al
  recorrido de esa petición original.

### Key Entities

- **Traza (Trace)**: representa el recorrido completo de una petición de
  extremo a extremo. Tiene un identificador único de correlación y está
  compuesta por uno o más Pasos.
- **Paso (Span)**: representa el trabajo realizado por un servicio
  específico dentro de una Traza. Atributos: servicio que lo ejecutó,
  duración, resultado (éxito/error), y su relación de orden/jerarquía
  respecto a otros Pasos de la misma Traza.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un operador puede ver el recorrido completo de una petición
  (desde el punto de entrada hasta el último servicio involucrado) en una
  sola consulta, sin cruzar logs de distintos servicios manualmente.
- **SC-002**: Un operador puede identificar cuál servicio causó una falla
  dentro de una cadena de llamadas en menos de 1 minuto de investigación.
- **SC-003**: El 100% de las peticiones de negocio se siguen procesando con
  normalidad aunque el sistema de trazabilidad esté completamente caído.
- **SC-004**: Un operador puede identificar, comparando dos peticiones
  equivalentes, en qué paso específico se concentra una diferencia de
  tiempo de respuesta.

## Assumptions

- Todos los servicios del sistema (`api-gateway`, `discovery-server`,
  `config-server`, `user-service`, `order-service`) participan en la
  trazabilidad; no se limita a un subconjunto.
- No se exige en esta especificación un mecanismo de alertas automáticas
  sobre las trazas; el alcance es visibilidad y consulta manual. Alertas
  proactivas quedarían como una feature futura.
- Dado que el volumen de tráfico de este sistema es bajo (entorno de
  demo/desarrollo), se asume captura completa de trazas; un mecanismo de
  muestreo (sampling) para reducir volumen es una decisión de la fase de
  planificación técnica, no un requisito de esta especificación.
- El tiempo de retención de las trazas no se define aquí; se asume un
  período razonable para diagnóstico de corto plazo (horas/días), a
  definir en la planificación técnica según el almacenamiento elegido.
