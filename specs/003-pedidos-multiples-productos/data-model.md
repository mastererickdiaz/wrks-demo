# Data Model: Pedidos con Múltiples Productos

## OrderLineItem (nuevo, dominio)

| Campo | Tipo | Reglas |
|---|---|---|
| `productName` | String | `@NotBlank` |
| `quantity` | Integer | `@Min(1)` |
| `unitPrice` | BigDecimal | `@NotNull`, `@Min(0)` |

Método calculado: `subtotal()` = `unitPrice × quantity`. No se persiste; se
calcula al construir la respuesta.

## OrderLineItemRequest (nuevo, DTO de entrada)

Misma forma que `OrderLineItem`, con las anotaciones de validación de
Jakarta Bean Validation. Es la forma en que el cliente envía cada línea en
`POST /api/orders`.

## Order (modificado)

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | Long | Generado en memoria (`AtomicLong`), sin cambios |
| `userId` | Long | Sin cambios (sigue validándose contra `user-service`, FR-011 de spec 001) |
| `items` | `List<OrderLineItem>` | **Nuevo** — reemplaza `productName`/`quantity`/`price`. `@NotEmpty` a nivel de `OrderRequest`; MUST tener al menos un elemento (FR-002) |
| `totalPrice` | BigDecimal | **Nuevo** — suma de `subtotal()` de todas las líneas. Calculado por el servidor, nunca aceptado del cliente (FR-003) |
| `status` | OrderStatus | Sin cambios (`PENDING\|CONFIRMED\|SHIPPED\|DELIVERED\|CANCELLED`, spec 002) |
| `createdAt` | LocalDateTime | Sin cambios |

### Relación con specs anteriores

- **Validación de usuario (spec 001, FR-011)**: no cambia; sigue
  ejecutándose antes de construir las líneas, sobre `request.userId()`.
- **Historial y transición de estado (spec 002)**: `OrderStatusHistoryEntry`
  no referencia `items` ni `totalPrice`; sigue operando solo sobre
  `fromStatus`/`toStatus`. Sin cambios.
- **Notificación (spec 002)**: `NotificationEvent` no incluye el detalle de
  productos; sigue refiriéndose solo al cambio de estado. Sin cambios.

## OrderRequest (modificado)

| Campo | Tipo | Reglas |
|---|---|---|
| `userId` | Long | `@NotNull` (sin cambios) |
| `items` | `List<OrderLineItemRequest>` | **Nuevo** — reemplaza `productName`/`quantity`/`price` planos. `@NotEmpty` (FR-002) + `@Valid` para que las reglas de cada línea se evalúen (cascada de validación) |

**Ruptura intencional**: el formato anterior (`productName: string,
quantity: number, price: number` planos) deja de aceptarse. No hay
compatibilidad dual (ver Assumptions de `spec.md`).
