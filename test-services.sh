#!/bin/bash

echo "🧪 Iniciando pruebas de integración..."

# Verificar requisitos
command -v jq >/dev/null 2>&1 || { echo "❌ Error: jq es requerido pero no está instalado."; exit 1; }

# Función para verificar salud de un servicio
check_service_health() {
    local service=$1
    local port=$2
    echo "Verificando $service (puerto $port)..."
    local health=$(curl -s http://localhost:$port/actuator/health)
    if echo $health | jq -e '.status == "UP"' > /dev/null; then
        echo "✅ $service está saludable"
        return 0
    else
        echo "❌ $service no está respondiendo correctamente"
        echo "Estado: $health"
        return 1
    fi
}

# Verificar todos los servicios
echo "🔍 Verificando estado de los servicios..."
check_service_health "Config Server" 8888
check_service_health "Discovery Server" 8761
check_service_health "API Gateway" 8080
check_service_health "User Service" 8081
check_service_health "Order Service" 8082

echo "📝 Ejecutando pruebas de API..."

# Crear usuario a través del API Gateway
echo "1️⃣ Creando usuario de prueba..."
USER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Juan Pérez",
    "email": "juan@example.com",
    "phone": "+123456789"
  }')

if [ $? -ne 0 ]; then
    echo "❌ Error al crear usuario"
    exit 1
fi

echo "Respuesta del servicio de usuarios:"
echo $USER_RESPONSE | jq .

USER_ID=$(echo $USER_RESPONSE | jq -r '.id')
if [ -z "$USER_ID" ]; then
    echo "❌ Error: No se pudo obtener el ID del usuario"
    exit 1
fi
echo "✅ Usuario creado con ID: $USER_ID"

# Crear orden usando el usuario creado
echo "2️⃣ Creando orden de prueba..."
ORDER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"$USER_ID\",
    \"productName\": \"Laptop Gaming\",
    \"quantity\": 1,
    \"price\": 999.99
  }")

if [ $? -ne 0 ]; then
    echo "❌ Error al crear orden"
    exit 1
fi

echo "Respuesta del servicio de órdenes:"
echo $ORDER_RESPONSE | jq .

# Verificar la integración
echo "3️⃣ Verificando datos creados..."

echo "📋 Listando usuarios:"
curl -s http://localhost:8080/api/users | jq .

echo "📋 Listando órdenes:"
curl -s http://localhost:8080/api/orders | jq .

# Verificar métricas y estado
echo "4️⃣ Verificando métricas de los servicios..."
echo "Métricas del API Gateway:"
curl -s http://localhost:8080/actuator/metrics/http.server.requests | jq .

echo "🏁 Pruebas completadas"
echo "Verificando Eureka:"
curl -s http://localhost:8761/eureka/apps | xmllint --format -