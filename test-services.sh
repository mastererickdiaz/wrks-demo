#!/bin/bash

# ==============================================================================
# Configuración
# ==============================================================================
API_GATEWAY_HOST="http://localhost:8080"
DISCOVERY_HOST="http://eureka-user:eureka-pass@localhost:8761" # Usar localhost y credenciales para acceso directo
CONFIG_HOST="http://localhost:8888"

# Puertos y nombres de servicios para la verificación de salud
declare -A SERVICES
SERVICES=(
    ["Config Server"]="8888"
    ["Discovery Server"]="8761"
    ["API Gateway"]="8080"
    ["User Service"]="8081"
    ["Order Service"]="8082"
)

# ==============================================================================
# Funciones
# ==============================================================================

# Función: Terminar con mensaje de error
fail() {
    echo "❌ ERROR: $1" >&2
    exit 1
}

# Función: Verificar requisitos (jq)
check_requirements() {
    echo "🔍 Verificando requisitos..."
    command -v jq >/dev/null 2>&1 || fail "jq es requerido y no está instalado."
    command -v curl >/dev/null 2>&1 || fail "curl es requerido y no está instalado."
    command -v xmllint >/dev/null 2>&1 || echo "⚠️ Advertencia: xmllint no está instalado. No se formateará la respuesta de Eureka."
}

# Función: Verificar salud de un servicio
check_service_health() {
    local service=$1
    local port=$2
    local health_url="http://localhost:$port/actuator/health"
    
    # Manejo especial para Discovery Server con autenticación (aunque el docker-compose ya lo tiene en su healthcheck)
    if [ "$service" == "Discovery Server" ]; then
        health_url="http://eureka-user:eureka-pass@localhost:$port/actuator/health"
    fi

    echo "   Verificando $service (puerto $port)..."
    local health=$(curl -s $health_url)
    
    if echo "$health" | jq -e '.status == "UP"' > /dev/null 2>&1; then
        echo "   ✅ $service está saludable"
        return 0
    else
        fail "$service no está respondiendo correctamente. Estado: $health"
    fi
}

# Función: Limpiar datos de prueba (opcional - requiere un endpoint de borrado)
cleanup_data() {
    if [ ! -z "$USER_ID" ]; then
        echo -e "\n🧹 Limpiando datos de prueba (Usuario ID: $USER_ID)..."
        # ¡IMPORTANTE! Este endpoint debe ser implementado en tu servicio para funcionar.
        # Por ahora, solo es un eco. Descomenta si tienes un DELETE /api/users/{id}
        # DELETE_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_GATEWAY_HOST/api/users/$USER_ID")
        # if [ "$DELETE_RESPONSE" == "204" ]; then
        #     echo "   ✅ Usuario $USER_ID eliminado."
        # else
        #     echo "   ❌ Falló la eliminación del usuario. Código de respuesta: $DELETE_RESPONSE"
        # fi
        echo "   (La limpieza de datos está deshabilitada en el script.)"
    fi
}

# ==============================================================================
# Ejecución Principal
# ==============================================================================

trap cleanup_data EXIT # Ejecutar limpieza al salir
check_requirements

echo "🚀 Iniciando pruebas de integración..."
echo "---"

## PRUEBA 1: Verificación de Salud
echo "## 1. Verificando estado de los microservicios..."
for service in "${!SERVICES[@]}"; do
    check_service_health "$service" "${SERVICES[$service]}"
done
echo "---"

## PRUEBA 2: Creación de Usuario (POST /api/users)
echo "## 2. Creando usuario de prueba (Juan Pérez)..."
USER_DATA='{
    "name": "Juan Pérez",
    "email": "juan-test-'"$RANDOM"'@example.com",
    "phone": "+123456789"
}'

USER_RESPONSE=$(curl -s -X POST "$API_GATEWAY_HOST/api/users" \
  -H "Content-Type: application/json" \
  -d "$USER_DATA")

# Verificar si la llamada HTTP fue exitosa y si la respuesta es JSON válida
HTTP_STATUS=$(echo "$USER_RESPONSE" | head -n 1 | awk '{print $2}')

if ! echo "$USER_RESPONSE" | jq -e '.id' > /dev/null 2>&1 || [ "$HTTP_STATUS" == "404" ]; then
    fail "Fallo al crear usuario. Respuesta: $USER_RESPONSE"
fi

USER_ID=$(echo "$USER_RESPONSE" | jq -r '.id')
echo "   ✅ Usuario creado. ID: $USER_ID"
echo "   Respuesta del servicio de usuarios:"
echo "$USER_RESPONSE" | jq .
echo "---"

## PRUEBA 3: Creación de Orden (POST /api/orders)
echo "## 3. Creando orden de prueba para el usuario $USER_ID..."
ORDER_DATA="{
    \"userId\": \"$USER_ID\",
    \"productName\": \"Laptop Gaming\",
    \"quantity\": 1,
    \"price\": 999.99
}"

ORDER_RESPONSE=$(curl -s -X POST "$API_GATEWAY_HOST/api/orders" \
  -H "Content-Type: application/json" \
  -d "$ORDER_DATA")

if ! echo "$ORDER_RESPONSE" | jq -e '.id' > /dev/null 2>&1; then
    fail "Fallo al crear orden. Respuesta: $ORDER_RESPONSE"
fi

ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.id')
echo "   ✅ Orden creada. ID: $ORDER_ID"
echo "   Respuesta del servicio de órdenes:"
echo "$ORDER_RESPONSE" | jq .
echo "---"

## PRUEBA 4: Verificación de Integración (Listar datos)
echo "## 4. Verificación de Integración (GET)..."
echo "   📋 Listando usuarios (Debería incluir a Juan Pérez):"
curl -s "$API_GATEWAY_HOST/api/users" | jq '.[] | select(.id == "'"$USER_ID"'")'
echo "   📋 Listando órdenes (Debería incluir la Orden $ORDER_ID):"
curl -s "$API_GATEWAY_HOST/api/orders" | jq '.[] | select(.id == "'"$ORDER_ID"'")'
echo "---"

## PRUEBA 5: Verificación de Eureka y Métricas
echo "## 5. Verificación de Eureka y Métricas..."
echo "   🔍 Estado de Eureka (Servicios registrados):"
# Usar credenciales en la URL para Eureka
if command -v xmllint >/dev/null 2>&1; then
    curl -s "$DISCOVERY_HOST/eureka/apps" | xmllint --format -
else
    curl -s "$DISCOVERY_HOST/eureka/apps"
fi
echo -e "\n   📊 Métricas del API Gateway (http.server.requests):"
curl -s "$API_GATEWAY_HOST/actuator/metrics/http.server.requests" | jq .
echo "---"

echo "🎉 Pruebas de integración completadas exitosamente."
exit 0