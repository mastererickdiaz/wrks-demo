# Data Model: Historial de Estados de Pedidos con Notificación al Usuario

## Order (existente, `order-service` — sin cambios de forma)

Sigue siendo el record ya definido en `001-plataforma-pedidos-usuarios`
(`id`, `userId`, `productName`, `quantity`, `price`, `status`, `createdAt`).
Como es inmutable, una transición de estado se implementa reemplazando la
instancia almacenada (mismo `id`, nuevo `status`), no mutando el objeto.

## OrderStatusHistoryEntry (nuevo)

| Campo | Tipo | Reglas |
|---|---|---|
| `orderId` | Long | Referencia al `Order` |
| `fromStatus` | `Order.OrderStatus` | Estado antes de la transición |
| `toStatus` | `Order.OrderStatus` | Estado después de la transición |
| `changedAt` | LocalDateTime | Momento de la transición |

Se almacena en una colección por `orderId`, ordenada por inserción
(equivalente a orden cronológico, ya que las transiciones se aplican
secuencialmente). Inmutable una vez creada (FR-003).

### Reglas de transición válidas (FR-002)

```
PENDING    -> CONFIRMED
CONFIRMED  -> SHIPPED
SHIPPED    -> DELIVERED
PENDING    -> CANCELLED
CONFIRMED  -> CANCELLED
```

Cualquier par `(fromStatus, toStatus)` no listado aquí (incluyendo
`toStatus == fromStatus`, retrocesos, y transiciones desde `SHIPPED` o
`DELIVERED` hacia `CANCELLED`) se rechaza con `InvalidOrderTransitionException`
(`400`).

## NotificationEvent (nuevo, conceptual — no persistido)

| Campo | Tipo | Reglas |
|---|---|---|
| `orderId` | Long | Pedido que cambió de estado |
| `recipientEmail` | String | Email del usuario, resuelto vía `UserClientService.getUserById(order.userId())` |
| `fromStatus` | `Order.OrderStatus` | |
| `toStatus` | `Order.OrderStatus` | |
| `occurredAt` | LocalDateTime | |

No se persiste; se construye y se publica (intenta entregarse) en el mismo
flujo de la transición, de forma asíncrona respecto a la respuesta HTTP. Si
`recipientEmail` no puede resolverse (usuario no encontrado/inactivo/
infraestructura caída), el evento se descarta y se registra como fallo de
notificación (FR-006) — la transición de estado ya se aplicó y no se ve
afectada.
