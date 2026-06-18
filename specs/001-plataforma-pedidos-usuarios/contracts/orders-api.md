# Contrato: API de Pedidos (`order-service`, expuesto vía `api-gateway`)

## `POST /api/orders`

**Request body**:
```json
{
  "userId": 1,
  "productName": "Teclado mecánico",
  "quantity": 2,
  "price": 89.90
}
```

**Respuestas**:

| Código | Cuándo | Body |
|---|---|---|
| `201 Created` | `userId` existe, `active=true`, datos válidos | `Order` creado (`status=PENDING`) |
| `400 Bad Request` | Validación de campos falla (`@Valid`) **o** el usuario existe pero `active=false` (NUEVO — FR-011) | Detalle del error de validación / `"Usuario inactivo"` |
| `404 Not Found` | `userId` no existe en `user-service` (NUEVO — FR-011) | `"Usuario no encontrado: {userId}"` |
| `201 Created` (modo degradado) | `user-service` no responde / circuito abierto (infraestructura, FR-008 — sin cambios) | `Order` creado vía fallback existente; el pedido se acepta porque es un fallo de infraestructura, no de negocio |

**Nota de diseño**: el código `400`/`404` para `userId` inválido (negocio) y el `201`
para infraestructura caída (FR-008) son intencionalmente distintos: una caída de
`user-service` no debe bloquear la operación del negocio, pero un `userId` que
sabemos que no existe o está inactivo sí debe rechazarse explícitamente.

**Dependencia interna**: para distinguir "no existe" de "existe pero inactivo",
`order-service` consulta `GET /api/users/{id}/raw` en `user-service` (no
`GET /api/users/{id}`, que excluye usuarios inactivos vía `findByIdAndActiveTrue`
y los trataría como inexistentes). Ver `UserController#getUserByIdIncludingInactive`.

## `GET /api/orders`

Sin cambios. `200 OK` con la lista de pedidos.

## `GET /api/orders/{id}`

Sin cambios. `200 OK` con el pedido, `404 Not Found` si no existe.
