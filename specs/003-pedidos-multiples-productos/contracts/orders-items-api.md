# Contrato: Pedidos con Múltiples Productos (`order-service`)

## `POST /api/orders` (contrato modificado — ruptura intencional)

**Request body (nuevo formato)**:
```json
{
  "userId": 1,
  "items": [
    { "productName": "Teclado mecánico", "quantity": 1, "unitPrice": 89.90 },
    { "productName": "Mouse", "quantity": 2, "unitPrice": 19.90 }
  ]
}
```

**Request body anterior (spec 001, YA NO VÁLIDO)**:
```json
{ "userId": 1, "productName": "Teclado mecánico", "quantity": 1, "price": 89.90 }
```

**Respuestas**:

| Código | Cuándo | Body |
|---|---|---|
| `201 Created` | `userId` válido y activo, `items` no vacío, todas las líneas válidas | `Order` con `items` y `totalPrice` calculado |
| `400 Bad Request` | `items` vacío, o alguna línea con `quantity < 1` o `unitPrice < 0`, o el `userId` existe pero está inactivo (sin cambios, spec 001) | Detalle de validación |
| `404 Not Found` | `userId` no existe (sin cambios, spec 001) | `"Usuario no encontrado: {userId}"` |

**Ejemplo de respuesta exitosa**:
```json
{
  "id": 1,
  "userId": 1,
  "items": [
    { "productName": "Teclado mecánico", "quantity": 1, "unitPrice": 89.90 },
    { "productName": "Mouse", "quantity": 2, "unitPrice": 19.90 }
  ],
  "totalPrice": 129.70,
  "status": "PENDING",
  "createdAt": "2026-06-18T10:00:00"
}
```

## `GET /api/orders` y `GET /api/orders/{id}`

Sin cambios de código en el controlador; el cuerpo de la respuesta ahora
incluye `items` y `totalPrice` en vez de `productName`/`quantity`/`price`,
heredando la nueva forma de `Order`.

## Endpoints de la spec 002 (sin cambios de contrato)

`PATCH /api/orders/{id}/status` y `GET /api/orders/{id}/history` no cambian
su request/response — siguen operando sobre el pedido completo y su
historial de estados, independientemente de cuántas líneas tenga.
