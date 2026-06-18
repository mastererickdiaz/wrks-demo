# Contrato: Transición de Estado e Historial de Pedidos (`order-service`)

## `PATCH /api/orders/{id}/status`

**Request body**:
```json
{ "status": "CONFIRMED" }
```

**Respuestas**:

| Código | Cuándo | Body |
|---|---|---|
| `200 OK` | Transición válida (ver reglas en `data-model.md`) | `Order` actualizado con el nuevo `status` |
| `400 Bad Request` | Transición no permitida (salto, retroceso, mismo estado, o desde un estado terminal) | `"Transición inválida: {fromStatus} -> {toStatus}"` |
| `404 Not Found` | El pedido `{id}` no existe | `"Pedido no encontrado: {id}"` |

**Efecto secundario**: si la transición es válida, se agrega una entrada al
historial del pedido y se intenta publicar una notificación al usuario
propietario (best-effort, no afecta el código de respuesta de este endpoint).

## `GET /api/orders/{id}/history`

**Respuestas**:

| Código | Cuándo | Body |
|---|---|---|
| `200 OK` | El pedido existe (con o sin transiciones aplicadas) | `[]` si no hay transiciones, o lista de `OrderStatusHistoryEntry` en orden cronológico |
| `404 Not Found` | El pedido `{id}` no existe | `"Pedido no encontrado: {id}"` |

**Ejemplo de respuesta con historial**:
```json
[
  { "orderId": 1, "fromStatus": "PENDING", "toStatus": "CONFIRMED", "changedAt": "2026-06-17T10:00:00" },
  { "orderId": 1, "fromStatus": "CONFIRMED", "toStatus": "SHIPPED", "changedAt": "2026-06-17T11:30:00" }
]
```

## Notificación (sin endpoint HTTP propio — efecto secundario interno)

Cuando una transición se aplica con éxito, `order-service` intenta entregar
un webhook HTTP `POST` a `notifications.webhook.url` (configurable) con este
cuerpo:

```json
{
  "orderId": 1,
  "recipientEmail": "ana@example.com",
  "fromStatus": "PENDING",
  "toStatus": "CONFIRMED",
  "occurredAt": "2026-06-17T10:00:00"
}
```

Si `notifications.webhook.enabled=false` (valor por defecto en desarrollo
local sin receptor configurado), se registra un log `INFO` simulando el
envío en vez de intentar la llamada HTTP real. Si está habilitado y la
llamada falla (timeout, 5xx, conexión rechazada), Resilience4j aplica
reintentos y luego degrada silenciosamente: se registra un `WARN`/`ERROR` y
no se relanza ninguna excepción hacia el flujo de la transición de estado
(FR-006).
