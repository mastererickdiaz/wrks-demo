# Quickstart de validación: Pedidos con Múltiples Productos

## Prerrequisitos

```bash
docker compose up --build
```

## 1. Crear un usuario

```bash
curl -s -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana Torres","email":"ana@example.com","phone":"555-0100"}'
```

## 2. Crear un pedido con varios productos

```bash
curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      { "productName": "Teclado mecánico", "quantity": 1, "unitPrice": 89.90 },
      { "productName": "Mouse", "quantity": 2, "unitPrice": 19.90 }
    ]
  }'
```

Esperado: `201 Created`, `totalPrice: 129.70` (89.90 + 2×19.90), `items` con
las 2 líneas en el mismo orden enviado.

## 3. Validar el rechazo de un pedido sin productos

```bash
curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": []}'
```

Esperado: `400 Bad Request`.

## 4. Validar el rechazo atómico ante una línea inválida

```bash
curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "items": [
      { "productName": "Teclado mecánico", "quantity": 1, "unitPrice": 89.90 },
      { "productName": "Producto inválido", "quantity": 0, "unitPrice": 10.0 }
    ]
  }'
```

Esperado: `400 Bad Request`; ningún pedido se crea (verificar con
`GET /api/orders` que no aparece ninguna línea suelta del "Teclado mecánico"
de esta solicitud).

## 5. Confirmar que el historial y la transición de estado (spec 002) siguen intactos

```bash
curl -s -i -X PATCH http://localhost:8082/api/orders/1/status \
  -H "Content-Type: application/json" -d '{"status":"CONFIRMED"}'

curl -s http://localhost:8082/api/orders/1/history
```

Esperado: `200 OK` en la transición, e historial con una entrada
`PENDING -> CONFIRMED`, igual que en pedidos de un solo producto antes de
esta feature.
