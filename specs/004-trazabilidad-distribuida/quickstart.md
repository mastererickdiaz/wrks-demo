# Quickstart de validación: Trazabilidad Distribuida de Peticiones

## Prerrequisitos

```bash
docker compose up --build
```

Esperar a que todos los servicios, incluyendo `jaeger`, reporten `healthy`/
estén arriba (`docker compose ps`).

## 1. Generar una petición que atraviese varios servicios

```bash
curl -s -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana Torres","email":"ana@example.com","phone":"555-0100"}'

curl -s -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productName":"Teclado","quantity":1,"unitPrice":89.90}]}'
```

Esta segunda llamada atraviesa `order-service` → `user-service` (validación
de usuario, FR-011 de spec 001) — es la petición ideal para verificar la
propagación entre dos servicios.

## 2. Consultar el recorrido completo en Jaeger UI

Abrir `http://localhost:16686`, buscar el servicio `order-service`, y
ubicar la traza más reciente del `POST /api/orders`.

Esperado (US1, SC-001): la traza muestra al menos dos spans — uno de
`order-service` (recibiendo el `POST`) y uno de la llamada saliente hacia
`user-service` (`GET /api/users/{id}/raw`), con una relación padre-hijo
clara, sin necesidad de cruzar logs de ambos servicios manualmente.

## 3. Medir la duración de cada paso

En la misma traza del paso 2, expandir cada span.

Esperado (US2, SC-004): cada span muestra su propia duración; la suma
aproximada de los spans hijos explica la duración total del span padre.

## 4. Validar que una falla se identifica sin cruzar logs

```bash
curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 9999, "items": [{"productName":"Mouse","quantity":1,"unitPrice":19.90}]}'
```

Esperado: `404` (comportamiento de negocio sin cambios, spec 001). En
Jaeger UI, la traza de esta petición debe marcar el span de la llamada a
`user-service` (o el span de `order-service` que la envuelve) como error,
identificable visualmente sin abrir ningún log.

## 5. Validar que la caída de Jaeger NO afecta el negocio (FR-005, gate del Principio IV)

```bash
docker compose stop jaeger

curl -s -i -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "items": [{"productName":"Monitor","quantity":1,"unitPrice":199.90}]}'

docker compose start jaeger
```

Esperado: el `POST /api/orders` sigue respondiendo `201 Created` con
normalidad, exactamente igual que con Jaeger arriba. Ninguna petición de
negocio debe fallar, demorarse perceptiblemente, ni devolver error por la
caída del backend de trazas.
