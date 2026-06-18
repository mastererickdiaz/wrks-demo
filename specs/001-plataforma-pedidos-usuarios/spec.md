# Feature Specification: Plataforma de Gestión de Usuarios y Pedidos (sistema existente)

**Feature Branch**: `001-plataforma-pedidos-usuarios`

**Created**: 2026-06-17

**Status**: Draft

**Input**: Documentar como baseline de Spec-Driven Development el sistema de microservicios
ya implementado en este repositorio: gestión de usuarios, gestión de pedidos, y la
infraestructura compartida (descubrimiento de servicios, configuración centralizada,
gestión de secretos y puerta de enlace única) que los soporta.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrar usuarios (Priority: P1)

Un cliente del sistema (aplicación interna o administrador) necesita crear, consultar,
actualizar y desactivar/eliminar usuarios para poder asociarles pedidos.

**Why this priority**: Sin usuarios no puede existir ningún pedido; es la base de datos
maestra que consume `order-service`.

**Independent Test**: Se puede probar de forma aislada llamando directamente a
`POST /api/users`, `GET /api/users`, `GET /api/users/{id}`, `PUT /api/users/{id}` y
`DELETE /api/users/{id}` en `user-service`, sin necesidad de que `order-service` esté
disponible.

**Acceptance Scenarios**:

1. **Given** no existe el usuario, **When** se envía `POST /api/users` con `name`, `email`
   y `phone` válidos, **Then** el sistema responde `201 Created` con el usuario creado,
   `active=true` por defecto y un `id` autogenerado.
2. **Given** el usuario existe, **When** se solicita `GET /api/users/{id}`, **Then** el
   sistema responde `200 OK` con los datos del usuario.
3. **Given** el usuario no existe, **When** se solicita `GET /api/users/{id}`, **Then** el
   sistema responde `404 Not Found`.
4. **Given** el usuario existe, **When** se envía `PUT /api/users/{id}` con datos
   modificados, **Then** el sistema actualiza el registro y responde `200 OK` con los
   datos actualizados.
5. **Given** el usuario existe, **When** se envía `DELETE /api/users/{id}`, **Then** el
   sistema responde `204 No Content`.
6. **Given** se envía un `email` o `name` vacío/inválido, **When** se intenta crear o
   actualizar el usuario, **Then** el sistema rechaza la solicitud por validación
   (`@NotBlank`, `@Email`).

---

### User Story 2 - Crear y consultar pedidos asociados a un usuario (Priority: P1)

Un cliente del sistema necesita registrar un pedido para un usuario existente
(producto, cantidad, precio) y luego consultar el estado y el historial de pedidos.

**Why this priority**: Es la funcionalidad de negocio principal del sistema; sin ella,
`order-service` no tiene razón de ser.

**Independent Test**: Se puede probar creando un pedido vía `POST /api/orders` con un
`userId` válido y verificando que aparece en `GET /api/orders` y en
`GET /api/orders/{id}`, incluyendo el caso en que `user-service` no esté disponible
(debe degradar con fallback, no fallar la creación del pedido).

**Acceptance Scenarios**:

1. **Given** un `userId` existente y datos válidos de producto, **When** se envía
   `POST /api/orders` con `userId`, `productName`, `quantity` (≥1) y `price` (≥0),
   **Then** el sistema responde `201 Created` con el pedido en estado `PENDING`,
   `id` autogenerado y `createdAt` con la fecha/hora de creación.
2. **Given** existen pedidos registrados, **When** se solicita `GET /api/orders`,
   **Then** el sistema responde `200 OK` con la lista completa de pedidos.
3. **Given** un pedido existente, **When** se solicita `GET /api/orders/{id}`,
   **Then** el sistema responde `200 OK` con los datos del pedido.
4. **Given** un pedido inexistente, **When** se solicita `GET /api/orders/{id}`,
   **Then** el sistema responde `404 Not Found`.
5. **Given** `user-service` no responde o falla, **When** se intenta crear un pedido,
   **Then** el circuito (Resilience4j) se abre tras los fallos configurados y la
   operación se degrada mediante un fallback en lugar de propagar un error 500 sin
   control.
6. **Given** un `userId` que no existe en `user-service`, **When** se envía
   `POST /api/orders` con ese `userId`, **Then** el sistema responde `404 Not Found`
   y NO crea el pedido.
7. **Given** un `userId` que existe pero está marcado como `active=false`, **When**
   se envía `POST /api/orders` con ese `userId`, **Then** el sistema rechaza la
   solicitud (`400`/`409`) y NO crea el pedido.

---

### User Story 3 - Acceso único y resiliente a través del Gateway (Priority: P2)

Un cliente externo necesita un único punto de entrada HTTP para acceder tanto a
`user-service` como a `order-service`, sin conocer su ubicación física ni gestionar
manualmente reintentos o límites de tráfico.

**Why this priority**: Es la fachada que protege y simplifica el consumo de los
servicios, pero el sistema ya es funcionalmente útil sin ella si se llama a cada
servicio directamente (por eso es P2, no P1).

**Independent Test**: Se puede verificar enviando tráfico al `api-gateway` (puerto 8080)
y comprobando que las peticiones se enrutan correctamente a `user-service` y
`order-service` registrados en Eureka, incluyendo el comportamiento de limitación de
tasa (rate limiting con Redis) y de circuit breaker ante caídas de los servicios
destino.

**Acceptance Scenarios**:

1. **Given** `user-service` está registrado en Eureka, **When** un cliente llama al
   `api-gateway` con la ruta correspondiente, **Then** la petición se reenvía a una
   instancia sana de `user-service`.
2. **Given** se excede el límite de tasa configurado (Redis), **When** un cliente envía
   peticiones por encima del umbral, **Then** el gateway responde con un código de
   limitación de tráfico en lugar de saturar los servicios backend.
3. **Given** un servicio backend no responde, **When** el gateway reenvía tráfico hacia
   él repetidamente, **Then** el circuit breaker se abre y el gateway responde con un
   fallback controlado en vez de colgarse esperando timeouts.

---

### Edge Cases

- ¿Qué ocurre si se crea un pedido (`order-service`) referenciando un `userId` que no
  existe en `user-service`? **Decisión**: `order-service` MUST validar el `userId`
  contra `user-service` (reutilizando el `UserServiceClient`/Feign ya existente)
  antes de crear el pedido, y MUST rechazar la solicitud (`404` si el usuario no
  existe, `400`/`409` si existe pero está inactivo) en lugar de crear el pedido
  igualmente como ocurre hoy.
- ¿Qué pasa con los pedidos al reiniciar `order-service`? Al almacenarse en una
  `List<Order>` en memoria (sin base de datos), todos los pedidos se pierden al
  reiniciar el contenedor/proceso.
- ¿Qué pasa si Vault no está disponible al arrancar `user-service` u `order-service`?
  El arranque falla porque la configuración de secretos (credenciales de BD, claves)
  se resuelve en el bootstrap contra Vault.
- ¿Qué pasa si `discovery-server` no está disponible? Los servicios no logran
  registrarse/descubrirse entre sí y el `api-gateway` no puede enrutar tráfico hacia
  ellos.
- ¿Qué pasa si se intenta eliminar un usuario que tiene pedidos asociados? No existe
  ninguna validación de integridad referencial entre `order-service` y `user-service`
  hoy; el usuario se elimina sin afectar los pedidos ya creados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir crear, listar, consultar por id, actualizar y
  eliminar usuarios mediante `user-service` (`/api/users`).
- **FR-002**: El sistema MUST validar que `name` y `email` de un usuario no estén vacíos
  y que `email` tenga formato válido antes de persistir.
- **FR-003**: El sistema MUST permitir crear pedidos asociados a un `userId`, con
  `productName`, `quantity` (mínimo 1) y `price` (mínimo 0), mediante `order-service`
  (`/api/orders`).
- **FR-004**: El sistema MUST asignar a cada pedido nuevo el estado inicial `PENDING` y
  una marca de tiempo de creación (`createdAt`).
- **FR-005**: El sistema MUST permitir listar todos los pedidos y consultar un pedido
  específico por id, respondiendo `404` si no existe.
- **FR-006**: El sistema MUST registrar (`Eureka`) cada servicio backend
  (`api-gateway`, `config-server`, `order-service`, `user-service`) para que puedan
  descubrirse entre sí dinámicamente.
- **FR-007**: El sistema MUST centralizar la configuración no sensible en
  `config-server` y los secretos (credenciales, claves) en Vault, sin texto plano en
  el código ni en `docker-compose.yml`.
- **FR-008**: El sistema MUST proteger la comunicación `order-service → user-service`
  con un mecanismo de circuit breaker y reintentos, degradando con un fallback
  cuando `user-service` no esté disponible, en lugar de fallar la creación del pedido
  sin control.
- **FR-011**: `order-service` MUST validar que el `userId` recibido en `POST /api/orders`
  exista y esté `active=true` en `user-service` antes de crear el pedido, rechazando
  la solicitud (`404` si no existe, `400`/`409` si está inactivo) en caso contrario.
- **FR-009**: El sistema MUST exponer un único punto de entrada externo
  (`api-gateway`) que enrute tráfico hacia `user-service` y `order-service`, incluyendo
  limitación de tasa (rate limiting).
- **FR-010**: El sistema MUST exponer un endpoint de salud (`/api/users/health`,
  `/api/orders/health`) por servicio para verificación operativa.

### Key Entities

- **Usuario (`User`)**: persona registrada en el sistema. Atributos: `id`, `name`,
  `email` (único conceptualmente, validado por formato), `phone`, `active`
  (booleano, `true` por defecto). Gestionado por `user-service`.
- **Pedido (`Order`)**: solicitud de compra de un producto asociada a un usuario.
  Atributos: `id`, `userId` (referencia al Usuario), `productName`, `quantity`,
  `price`, `status` (`PENDING | CONFIRMED | SHIPPED | DELIVERED | CANCELLED`),
  `createdAt`. Gestionado por `order-service`. Hoy no tiene relación de integridad
  referencial forzada contra `Usuario`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cliente puede crear un usuario y luego crear un pedido para ese
  usuario en menos de 5 peticiones HTTP totales (crear usuario, crear pedido,
  confirmar lectura del pedido).
- **SC-002**: El sistema mantiene disponible la creación de pedidos aun cuando
  `user-service` está temporalmente caído, gracias al fallback de resiliencia
  (0% de errores no controlados propagados al cliente en ese escenario).
- **SC-003**: Toda petición externa pasa por un único punto de entrada
  (`api-gateway`); no existe ninguna ruta documentada de acceso directo a
  `user-service` u `order-service` desde fuera de la red de contenedores.
- **SC-004**: Ningún secreto (credenciales, claves de cifrado) aparece en texto
  plano en el control de versiones; el 100% de los secretos se resuelven en
  tiempo de arranque desde Vault.

## Assumptions

- Esta especificación documenta el comportamiento **actual observado en el código**
  (`user-service`, `order-service`, `api-gateway`, `discovery-server`,
  `config-server`, `vault`) como punto de partida para Spec-Driven Development,
  no una funcionalidad nueva a construir desde cero.
- El almacenamiento en memoria de `order-service` (sin base de datos persistente)
  se asume como limitación conocida de la demo actual, no como requisito permanente.
- Se asume que la ausencia de pruebas automatizadas es deuda técnica a resolver en
  features futuras, conforme al Principio IV (Disciplina de Pruebas) de la
  constitución del proyecto, y no una decisión de diseño deliberada.
- Se asume que, cuando `user-service` está temporalmente caído (circuito abierto),
  `order-service` debe tratar la indisponibilidad como un fallo del propio sistema
  (FR-008, error controlado/fallback de infraestructura) y no como "usuario
  inexistente" (FR-011, rechazo de negocio) — son dos causas distintas de fallo y
  deben distinguirse en la implementación (p. ej. no reutilizar el fallback actual
  de "usuario dummy" para decidir si el pedido se crea).
- El alcance de esta spec es el sistema tal como existe hoy; no incluye autenticación
  de usuarios finales de la API (solo Basic Auth interno entre `discovery-server` y
  sus clientes), pagos, ni notificaciones — estas son funcionalidades fuera de alcance
  hasta que se especifiquen como features nuevas.
