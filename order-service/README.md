# Order Service

Microservicio de ejemplo que gestiona pedidos.

Requisitos

- Java 25 (ver `pom.xml`).
- Maven (se incluye `mvnw`) o Docker.

Compilar y ejecutar

```powershell
cd order-service
.\mvnw.cmd clean package
java -jar target\order-service-1.0.0.jar
```

Construir imagen Docker

```bash
docker build -t order-service:1.0.0 .
```

Notas

- El `HELP.md` incluye información del POM padre y overrides añadidos para evitar heredar `<license>` y `<developers>`.

API: `POST /api/orders`

Un pedido contiene una o más líneas de producto (`items`); el total se
calcula en el servidor, nunca lo envía el cliente:

```json
{
  "userId": 1,
  "items": [
    { "productName": "Teclado mecánico", "quantity": 1, "unitPrice": 89.90 },
    { "productName": "Mouse", "quantity": 2, "unitPrice": 19.90 }
  ]
}
```

| Código | Cuándo |
|---|---|
| `201 Created` | El pedido se crea (usuario válido y activo, o `user-service` caído → modo degradado) |
| `400 Bad Request` | `items` vacío, alguna línea inválida (cantidad/precio fuera de rango), o el `userId` existe pero está inactivo (`active=false`) |
| `404 Not Found` | El `userId` no existe en `user-service` |

El formato anterior de un solo producto (`productName`/`quantity`/`price`
planos) ya no es válido (ruptura intencional, ver
`../specs/003-pedidos-multiples-productos/`). Detalle completo en
`../specs/001-plataforma-pedidos-usuarios/contracts/orders-api.md` (reglas de
usuario, sin cambios) y `../specs/003-pedidos-multiples-productos/contracts/orders-items-api.md`
(formato de líneas).

API: `PATCH /api/orders/{id}/status`

Transiciones válidas: `PENDING→CONFIRMED`, `CONFIRMED→SHIPPED`, `SHIPPED→DELIVERED`,
`PENDING→CANCELLED`, `CONFIRMED→CANCELLED`.

| Código | Cuándo |
|---|---|
| `200 OK` | Transición válida; el pedido se actualiza y se registra en el historial |
| `400 Bad Request` | Transición no permitida (salto, retroceso, o desde un estado terminal) |
| `404 Not Found` | El pedido no existe |

API: `GET /api/orders/{id}/history`

Devuelve la lista de transiciones de estado del pedido en orden cronológico
(`[]` si no tiene ninguna). `404` si el pedido no existe.

Cada transición válida intenta notificar al usuario propietario vía webhook
(`notifications.webhook.*`, deshabilitado por defecto → se simula con log). Un
fallo de notificación nunca afecta la respuesta de la transición.

Ver detalle completo en `../specs/002-historial-estados-pedido/contracts/orders-status-api.md`.
