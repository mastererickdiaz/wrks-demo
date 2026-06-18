# Feature Specification: Historial de Estados de Pedidos con Notificación al Usuario

**Feature Branch**: `002-historial-estados-pedido`

**Created**: 2026-06-17

**Status**: Draft

**Input**: Quiero que los pedidos puedan tener un historial de estados (PENDING →
CONFIRMED → SHIPPED → DELIVERED...) con notificación al usuario cuando el estado
cambia.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Avanzar el estado de un pedido (Priority: P1)

Un cliente del sistema (operación interna, sin rol de usuario final) necesita
mover un pedido de un estado al siguiente del flujo de negocio (por ejemplo,
de `PENDING` a `CONFIRMED`) y que ese cambio quede registrado de forma
permanente.

**Why this priority**: Sin la capacidad de cambiar el estado, no existe
"historial" que construir; es el cimiento de toda la feature.

**Independent Test**: Se puede probar creando un pedido y solicitando una
transición de estado válida; se verifica que el pedido refleja el nuevo
estado y que aparece una entrada nueva en su historial.

**Acceptance Scenarios**:

1. **Given** un pedido en estado `PENDING`, **When** se solicita la
   transición a `CONFIRMED`, **Then** el pedido queda en `CONFIRMED` y se
   agrega una entrada al historial con estado anterior `PENDING`, estado
   nuevo `CONFIRMED` y la fecha/hora del cambio.
2. **Given** un pedido en estado `CONFIRMED`, **When** se solicita la
   transición a `SHIPPED`, **Then** el pedido avanza y se registra la entrada
   correspondiente.
3. **Given** un pedido en estado `SHIPPED`, **When** se solicita la
   transición a `DELIVERED`, **Then** el pedido avanza y se registra la
   entrada correspondiente.
4. **Given** un pedido en estado `PENDING` o `CONFIRMED`, **When** se
   solicita la transición a `CANCELLED`, **Then** el pedido queda
   `CANCELLED` y se registra la entrada correspondiente.
5. **Given** un pedido en estado `SHIPPED` o `DELIVERED`, **When** se
   solicita la transición a `CANCELLED`, **Then** el sistema rechaza la
   solicitud y el historial no cambia (ver FR-002).
6. **Given** un pedido inexistente, **When** se solicita una transición de
   estado, **Then** el sistema responde `404 Not Found`.

---

### User Story 2 - Consultar el historial de un pedido (Priority: P1)

Un cliente del sistema necesita ver la secuencia completa de cambios de
estado de un pedido, en orden cronológico, para auditoría o soporte.

**Why this priority**: El historial solo tiene valor si puede consultarse;
sin esta capacidad, los datos registrados en US1 son invisibles.

**Independent Test**: Se puede probar realizando varias transiciones sobre
un pedido y verificando que el endpoint de historial devuelve todas las
entradas en el orden en que ocurrieron.

**Acceptance Scenarios**:

1. **Given** un pedido con dos transiciones aplicadas (`PENDING→CONFIRMED`,
   `CONFIRMED→SHIPPED`), **When** se consulta su historial, **Then** el
   sistema devuelve ambas entradas en orden cronológico ascendente.
2. **Given** un pedido recién creado sin transiciones aplicadas, **When** se
   consulta su historial, **Then** el sistema devuelve una lista vacía (no
   un error).
3. **Given** un pedido inexistente, **When** se consulta su historial,
   **Then** el sistema responde `404 Not Found`.

---

### User Story 3 - Notificar al usuario cuando cambia el estado (Priority: P2)

Cuando el estado de un pedido cambia exitosamente, el usuario propietario del
pedido (identificado por `userId`, con su email obtenido de `user-service`)
debe recibir una notificación informando el nuevo estado.

**Why this priority**: Aporta valor al usuario final, pero el sistema ya es
útil sin ella si solo se necesita el historial auditable de US1/US2 (por eso
es P2, no P1).

**Independent Test**: Se puede probar verificando que, tras una transición de
estado válida, se publica un evento de notificación dirigido al email del
usuario, sin necesidad de un proveedor de entrega real conectado (se puede
verificar la publicación del evento de forma aislada).

**Acceptance Scenarios**:

1. **Given** una transición de estado válida sobre un pedido de un usuario
   activo, **When** la transición se aplica, **Then** el sistema publica un
   evento de notificación con el email del usuario, el `orderId` y el nuevo
   estado.
2. **Given** que la publicación del evento de notificación falla (por
   ejemplo, el destino no está disponible), **When** se aplica una
   transición de estado válida, **Then** la transición se confirma igual al
   cliente HTTP (no se revierte ni se bloquea) y el fallo de notificación se
   registra para diagnóstico.
3. **Given** un usuario inactivo o no encontrado, **When** se aplica una
   transición de estado sobre uno de sus pedidos, **Then** la transición se
   aplica igual y el fallo al notificar (usuario no contactable) se registra
   sin afectar la respuesta al cliente.

---

### Edge Cases

- ¿Qué pasa si se solicita una transición al mismo estado actual (por
  ejemplo, `PENDING` → `PENDING`)? Se trata como transición inválida (no es
  un avance del flujo) y se rechaza igual que un salto de estado.
- ¿Qué pasa si se solicita una transición "hacia atrás" (por ejemplo,
  `SHIPPED` → `PENDING`)? Se rechaza; solo se permite el avance secuencial o
  la cancelación temprana (ver FR-002).
- ¿Qué pasa si el pedido ya está `CANCELLED` o `DELIVERED` (estados
  terminales) y se solicita cualquier transición? Se rechaza; son estados
  finales sin transiciones salientes.
- ¿Qué pasa si `user-service` no responde al momento de notificar? La
  transición de estado ya se aplicó (es independiente); solo falla el envío
  de la notificación, que se registra como incidente sin reintentar
  automáticamente en el alcance de esta feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST exponer una operación para solicitar la
  transición de estado de un pedido existente, indicando el estado destino.
- **FR-002**: El sistema MUST validar la transición contra las siguientes
  reglas antes de aplicarla, rechazando cualquier otra combinación:
  - `PENDING` → `CONFIRMED`
  - `CONFIRMED` → `SHIPPED`
  - `SHIPPED` → `DELIVERED`
  - `PENDING` → `CANCELLED`
  - `CONFIRMED` → `CANCELLED`
- **FR-003**: Toda transición de estado aplicada exitosamente MUST generar
  una entrada de historial inmutable con: estado anterior, estado nuevo, y
  fecha/hora del cambio.
- **FR-004**: El sistema MUST exponer una operación para consultar el
  historial completo de un pedido, ordenado cronológicamente.
- **FR-005**: Toda transición de estado aplicada exitosamente MUST disparar
  la publicación de un evento de notificación dirigido al usuario propietario
  del pedido (resuelto vía `user-service`), conteniendo como mínimo:
  `orderId`, `userId`/email del destinatario, estado anterior y estado nuevo.
- **FR-006**: Un fallo al publicar el evento de notificación MUST NOT revertir
  ni bloquear la transición de estado ya aplicada; el fallo MUST quedar
  registrado para diagnóstico.
- **FR-007**: Una transición inválida (no listada en FR-002) o sobre un
  pedido inexistente MUST rechazarse sin modificar el historial, con un
  código de error claro (`400` para transición inválida, `404` para pedido
  inexistente).
- **FR-008**: La consulta de historial sobre un pedido sin transiciones
  aplicadas MUST devolver una lista vacía, no un error.

### Key Entities

- **Entrada de Historial de Estado**: registro inmutable asociado a un
  pedido. Atributos: `orderId`, estado anterior, estado nuevo, fecha/hora del
  cambio. Se acumulan en orden cronológico por pedido.
- **Evento de Notificación**: representa la intención de avisar al usuario de
  un cambio de estado. Atributos: `orderId`, destinatario (email del
  usuario), estado anterior, estado nuevo, fecha/hora. El medio de entrega
  final (correo, webhook, cola, etc.) es una decisión de diseño técnico fuera
  del alcance de esta especificación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un pedido puede recorrer todo su ciclo de vida normal
  (`PENDING→CONFIRMED→SHIPPED→DELIVERED`) y su historial completo puede
  consultarse en una sola llamada adicional.
- **SC-002**: El 100% de las transiciones de estado válidas generan un
  evento de notificación, verificable de forma independiente al proveedor de
  entrega final.
- **SC-003**: El 0% de las transiciones inválidas (fuera de las reglas de
  FR-002) modifica el estado o el historial de un pedido.
- **SC-004**: Un fallo en la notificación nunca causa que una transición de
  estado válida sea rechazada al cliente que la solicitó.

## Assumptions

- No existe todavía autenticación/autorización en el sistema (ver spec
  `001-plataforma-pedidos-usuarios`); esta feature no introduce control de
  acceso sobre quién puede solicitar una transición de estado.
- El historial de estados se almacena con la misma estrategia que los
  pedidos hoy (en memoria, dentro de `order-service`); se pierde al
  reiniciar el servicio. Esto es una limitación conocida heredada, no una
  decisión nueva de esta feature.
- El "evento de notificación" se especifica como una abstracción a nivel de
  negocio (qué información debe llevar y cuándo debe dispararse); la
  implementación técnica concreta del transporte (cola de mensajes, webhook
  HTTP saliente, simulación por log, etc.) se decide en la fase de
  planificación técnica (`/speckit.plan`), no en esta especificación.
- Los estados de pedido siguen siendo los cinco ya definidos en el sistema
  (`PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`); esta feature
  no agrega estados nuevos.
- Se asume que un pedido cancelado o entregado no debe poder reabrirse ni
  reutilizarse para nuevas transiciones.
