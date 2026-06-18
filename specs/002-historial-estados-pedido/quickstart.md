# Quickstart de validación: Historial de Estados de Pedidos con Notificación

## Prerrequisitos

```bash
docker compose up --build
```

Esperar a que todos los servicios reporten `healthy` (`docker compose ps`).

## 1. Crear un usuario y un pedido

```bash
curl -s -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana Torres","email":"ana@example.com","phone":"555-0100"}'

curl -s -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"productName":"Teclado mecánico","quantity":1,"price":89.90}'
```

Esperado: pedido creado con `id:1`, `status:"PENDING"`.

## 2. Avanzar el estado (PENDING → CONFIRMED → SHIPPED → DELIVERED)

```bash
curl -s -i -X PATCH http://localhost:8082/api/orders/1/status \
  -H "Content-Type: application/json" -d '{"status":"CONFIRMED"}'

curl -s -i -X PATCH http://localhost:8082/api/orders/1/status \
  -H "Content-Type: application/json" -d '{"status":"SHIPPED"}'

curl -s -i -X PATCH http://localhost:8082/api/orders/1/status \
  -H "Content-Type: application/json" -d '{"status":"DELIVERED"}'
```

Esperado: cada llamada responde `200 OK` con el pedido reflejando el nuevo
`status`.

## 3. Consultar el historial completo

```bash
curl -s http://localhost:8082/api/orders/1/history
```

Esperado: lista con 3 entradas en orden cronológico
(`PENDING→CONFIRMED`, `CONFIRMED→SHIPPED`, `SHIPPED→DELIVERED`).

## 4. Validar el rechazo de transiciones inválidas

```bash
# Salto de estado (PENDING directo a SHIPPED) sobre un pedido nuevo
curl -s -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"productName":"Mouse","quantity":1,"price":19.90}'

curl -s -i -X PATCH http://localhost:8082/api/orders/2/status \
  -H "Content-Type: application/json" -d '{"status":"SHIPPED"}'
```

Esperado: `400 Bad Request`, el pedido permanece en `PENDING` y su
historial sigue vacío.

```bash
# Cancelar un pedido ya entregado (estado terminal)
curl -s -i -X PATCH http://localhost:8082/api/orders/1/status \
  -H "Content-Type: application/json" -d '{"status":"CANCELLED"}'
```

Esperado: `400 Bad Request` (DELIVERED es terminal).

```bash
# Pedido inexistente
curl -s -i -X PATCH http://localhost:8082/api/orders/9999/status \
  -H "Content-Type: application/json" -d '{"status":"CONFIRMED"}'
```

Esperado: `404 Not Found`.

## 5. Verificar que la notificación no bloquea la transición

Con `notifications.webhook.enabled=false` (default sin configurar un
receptor), cada transición válida debe registrar en los logs de
`order-service` una línea de notificación simulada:

```bash
docker compose logs order-service | grep -i "notificaci"
```

Esperado: una línea por cada transición válida del paso 2, sin que ninguna
de esas transiciones haya fallado o respondido con error.
