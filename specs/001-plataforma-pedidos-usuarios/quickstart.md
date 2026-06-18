# Quickstart de validación: Plataforma de Usuarios y Pedidos

## Prerrequisitos

```bash
docker compose up --build
```

Esperar a que `discovery-server`, `config-server`, `vault`, `user-service`,
`order-service` y `api-gateway` reporten healthcheck OK (`docker-compose ps`).

## 1. Crear un usuario

```bash
curl -s -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana Torres","email":"ana@example.com","phone":"555-0100"}'
```

Esperado: `201 Created` con `id` generado y `active: true`.

## 2. Crear un pedido para ese usuario (camino feliz)

```bash
curl -s -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "productName":"Teclado mecánico","quantity":2,"price":89.90}'
```

Esperado: `201 Created`, `status: PENDING`.

## 3. Validar el rechazo por `userId` inexistente (FR-011, nuevo)

```bash
curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 9999, "productName":"Mouse","quantity":1,"price":19.90}'
```

Esperado: `404 Not Found` (hoy, antes de implementar FR-011, este caso responde
`201 Created` — ver `OrderService.createOrder` actual).

## 4. Validar el rechazo por usuario inactivo (FR-011, nuevo)

```bash
# Desactivar el usuario (ejemplo conceptual; requiere PUT con active=false)
curl -s -X PUT http://localhost:8081/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana Torres","email":"ana@example.com","phone":"555-0100","active":false}'

curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "productName":"Monitor","quantity":1,"price":199.90}'
```

Esperado: `400 Bad Request` (o `409`, según se confirme en `contracts/orders-api.md`).

## 5. Validar la degradación cuando `user-service` está caído (FR-008, sin cambios)

```bash
docker-compose stop user-service
curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "productName":"Webcam","quantity":1,"price":59.90}'
docker-compose start user-service
```

Esperado: el circuito de Resilience4j se abre tras los reintentos configurados y la
orden se acepta (`201 Created`) vía el fallback existente — comportamiento de
infraestructura, distinto del rechazo de negocio de los pasos 3 y 4.

## 6. Verificar el punto de entrada único (api-gateway)

```bash
curl -s http://localhost:8080/api/users
curl -s http://localhost:8080/api/orders
```

Esperado: mismas respuestas que llamando directamente a los puertos 8081/8082,
confirmando que el gateway enruta correctamente vía Eureka.
