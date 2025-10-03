#!/bin/bash

echo "Probando servicios..."

# Esperar a que los servicios estén listos
echo "Esperando a que los servicios estén listos..."
sleep 1

# Verificar salud de los servicios
echo "Verificando salud de los servicios:"
curl -s http://localhost:8081/actuator/health | jq .
curl -s http://localhost:8082/actuator/health | jq .

# Crear usuario
echo "Creando usuario..."
USER_RESPONSE=$(curl -s -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Juan Perez",
    "email": "juan@example.com",
    "phone": "+123456789"
  }')

echo "Respuesta del usuario: $USER_RESPONSE"

USER_ID=$(echo $USER_RESPONSE | grep -o '"id":[0-9]*' | cut -d: -f2)
echo "Usuario creado con ID: $USER_ID"

# Crear orden
echo "Creando orden..."
ORDER_RESPONSE=$(curl -s -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": $USER_ID,
    \"productName\": \"Laptop Gaming\",
    \"quantity\": 1,
    \"price\": 999.99
  }")

echo "Respuesta de la orden: $ORDER_RESPONSE"

echo ""
echo "Listando órdenes:"
curl -s http://localhost:8082/api/orders | jq .

echo ""
echo "Listando usuarios:"
curl -s http://localhost:8081/api/users | jq .

echo ""
echo "Verificando Eureka:"
curl -s http://localhost:8761/eureka/apps | xmllint --format -