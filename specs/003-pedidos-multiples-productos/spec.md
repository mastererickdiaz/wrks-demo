# Feature Specification: Pedidos con Múltiples Productos

**Feature Branch**: `003-pedidos-multiples-productos`

**Created**: 2026-06-17

**Status**: Draft

**Input**: Permitir que un pedido contenga varios productos (carrito), en vez de
un solo producto por pedido como hoy.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear un pedido con varios productos (Priority: P1)

Un cliente del sistema necesita registrar un pedido que incluya varios
productos distintos (cada uno con su propia cantidad y precio unitario) en
una sola operación, en vez de tener que crear un pedido separado por cada
producto.

**Why this priority**: Es el cambio central de la feature; sin esto, no hay
"carrito" que mostrar ni que mantener consistente en estado/historial.

**Independent Test**: Se puede probar enviando una solicitud de creación de
pedido con una lista de 2 o más productos y verificando que el pedido
resultante contiene todas las líneas y un total calculado correctamente.

**Acceptance Scenarios**:

1. **Given** un usuario activo, **When** se crea un pedido con 3 líneas de
   producto (cada una con nombre, cantidad y precio unitario válidos),
   **Then** el sistema responde `201 Created` con un pedido que contiene las
   3 líneas y un total igual a la suma de `cantidad × precio unitario` de
   cada línea.
2. **Given** un usuario activo, **When** se crea un pedido con una sola línea
   de producto, **Then** el sistema lo acepta igual (una línea es el caso
   mínimo válido, no un caso especial).
3. **Given** una solicitud de pedido sin ninguna línea de producto, **When**
   se intenta crear el pedido, **Then** el sistema la rechaza sin persistir
   nada.
4. **Given** una solicitud con una línea cuya cantidad es menor a 1 o cuyo
   precio unitario es negativo, **When** se intenta crear el pedido,
   **Then** el sistema rechaza la solicitud completa (no crea un pedido
   parcial con solo las líneas válidas).
5. **Given** un `userId` inexistente o inactivo, **When** se intenta crear un
   pedido con varias líneas, **Then** se aplican las mismas reglas de
   rechazo ya existentes (404/400) descritas en la especificación
   `001-plataforma-pedidos-usuarios` — sin cambios por esta feature.

---

### User Story 2 - Consultar un pedido con el detalle de sus productos (Priority: P1)

Un cliente del sistema necesita ver, al consultar un pedido (individual o en
listado), todas sus líneas de producto y el total calculado, no solo un
producto y un precio como hoy.

**Why this priority**: Sin poder ver el detalle completo, la información
capturada en US1 no tiene utilidad práctica.

**Independent Test**: Se puede probar creando un pedido con varias líneas y
verificando que tanto `GET /api/orders/{id}` como `GET /api/orders` muestran
cada línea por separado junto con el total.

**Acceptance Scenarios**:

1. **Given** un pedido con 2 líneas de producto, **When** se consulta por
   id, **Then** la respuesta incluye ambas líneas (nombre, cantidad, precio
   unitario, subtotal) y el total del pedido.
2. **Given** varios pedidos multi-producto existentes, **When** se listan
   todos los pedidos, **Then** cada uno en la lista muestra sus propias
   líneas y su propio total, sin mezclarse entre pedidos.

---

### Edge Cases

- ¿Qué pasa si dos líneas del mismo pedido tienen el mismo nombre de
  producto? Se permiten como líneas independientes; el sistema no las fusiona
  automáticamente en una sola cantidad.
- ¿Qué pasa si una línea tiene cantidad 0, negativa, o precio unitario
  negativo? Se rechaza toda la solicitud de creación (atomicidad: no se
  crean pedidos parcialmente válidos).
- ¿Qué pasa con el historial de estados (`002-historial-estados-pedido`) y
  las reglas de transición sobre un pedido multi-producto? No cambian; el
  historial y las transiciones operan sobre el pedido como un todo,
  independientemente de cuántas líneas tenga.
- ¿Qué pasa con pedidos creados antes de esta feature, con el formato
  anterior de un solo producto? No aplica: los pedidos se almacenan en
  memoria y se pierden al reiniciar `order-service` (limitación ya conocida
  y documentada), por lo que no hay datos previos que migrar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir crear un pedido especificando una
  lista de una o más líneas de producto, cada una con nombre de producto,
  cantidad (mínimo 1) y precio unitario (mínimo 0).
- **FR-002**: El sistema MUST rechazar la creación de un pedido sin ninguna
  línea de producto.
- **FR-003**: El sistema MUST calcular el total del pedido como la suma de
  `cantidad × precio unitario` de todas sus líneas; el total NUNCA se acepta
  como dato de entrada del cliente.
- **FR-004**: El sistema MUST rechazar la creación completa del pedido si
  alguna línea tiene cantidad menor a 1 o precio unitario negativo (no se
  permiten pedidos parcialmente creados).
- **FR-005**: El sistema MUST conservar el orden en que las líneas fueron
  enviadas al consultar el pedido posteriormente.
- **FR-006**: Al consultar un pedido (individual o en listado), el sistema
  MUST incluir todas sus líneas de producto (con su subtotal) y el total
  calculado.
- **FR-007**: Las reglas de validación de usuario ya existentes (rechazo por
  `userId` inexistente o inactivo, ver spec `001-plataforma-pedidos-usuarios`)
  MUST seguir aplicándose sin cambios sobre la creación de pedidos
  multi-producto.
- **FR-008**: Las reglas de transición de estado e historial ya existentes
  (ver spec `002-historial-estados-pedido`) MUST seguir aplicándose sin
  cambios sobre pedidos multi-producto, operando sobre el pedido completo
  (no por línea de producto).

### Key Entities

- **Línea de Pedido (Order Line Item)**: nombre del producto, cantidad,
  precio unitario, y subtotal (calculado como cantidad × precio unitario).
  Pertenece a un único pedido.
- **Pedido (Order, modificado)**: ya no tiene un solo producto/cantidad/
  precio; en su lugar contiene una lista ordenada de Líneas de Pedido y un
  total calculado (suma de los subtotales). El resto de sus atributos
  (`userId`, estado, fecha de creación) no cambian.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cliente puede registrar un pedido con varios productos
  distintos en una sola solicitud, sin necesidad de calcular el total
  manualmente.
- **SC-002**: El 100% de los totales de pedido devueltos por el sistema
  coinciden exactamente con la suma de sus líneas (cantidad × precio
  unitario), sin depender de un total enviado por el cliente.
- **SC-003**: El 0% de las solicitudes con líneas inválidas (cantidad o
  precio fuera de rango, o lista vacía) resulta en un pedido persistido,
  parcial o completo.
- **SC-004**: Las capacidades ya existentes de historial de estados y
  transición (spec 002) siguen funcionando sin modificación sobre pedidos
  con múltiples productos.

## Assumptions

- Esta feature **reemplaza** el formato anterior de pedido de un solo
  producto (`productName`/`quantity`/`price` planos, definido en la spec
  `001-plataforma-pedidos-usuarios`); no se mantiene compatibilidad con
  ambos formatos a la vez, ya que el sistema no tiene clientes externos
  reales ni versionado de API que lo exija.
- Los pedidos siguen almacenándose en memoria dentro de `order-service`
  (limitación conocida y heredada, sin cambios por esta feature).
- No se define un límite máximo de líneas por pedido en esta especificación;
  se asume un volumen razonable (decenas de líneas), no miles.
- El precio unitario y el total se expresan en la misma unidad monetaria ya
  usada hoy en el sistema (sin soporte multi-moneda).
