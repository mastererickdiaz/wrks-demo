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
    ["Product Service"]="8083"
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

## PRUEBA 2.5: Creación de Producto (POST /api/products)
echo "## 2.5. Creando producto de prueba (Laptop Pro)..."
PRODUCT_DATA='{
    "name": "Laptop Pro",
    "description": "Powerful laptop for professionals",
    "price": 1499.99
}'

PRODUCT_RESPONSE=$(curl -s -X POST "$API_GATEWAY_HOST/api/products" \
  -H "Content-Type: application/json" \
  -d "$PRODUCT_DATA")

if ! echo "$PRODUCT_RESPONSE" | jq -e '.id' > /dev/null 2>&1; then
    fail "Fallo al crear producto. Respuesta: $PRODUCT_RESPONSE"
fi

PRODUCT_ID=$(echo "$PRODUCT_RESPONSE" | jq -r '.id')
echo "   ✅ Producto creado. ID: $PRODUCT_ID"
echo "   Respuesta del servicio de productos:"
echo "$PRODUCT_RESPONSE" | jq .
echo "---"

## PRUEBA 3: Creación de Orden (POST /api/orders)
echo "## 3. Creando orden de prueba para el usuario $USER_ID y producto $PRODUCT_ID..."
ORDER_DATA="{
    \"userId\": \"$USER_ID\",
    \"productId\": \"$PRODUCT_ID\",
    \"quantity\": 1
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
echo "   📋 Listando productos (Debería incluir a Laptop Pro):"
curl -s "$API_GATEWAY_HOST/api/products" | jq '.[] | select(.id == "'"$PRODUCT_ID"'")'
echo "   📋 Listando órdenes (Debería incluir la Orden $ORDER_ID):"
curl -s "$API_GATEWAY_HOST/api/orders" | jq '.[] | select(.id == "'"$ORDER_ID"'")'
echo "---"

## PRUEBA 4.1: Actualización de Usuario (PUT /api/users/{id})
echo "## 4.1. Actualizando usuario de prueba (Juan Pérez -> Juan Pérez Actualizado)..."
UPDATED_USER_DATA='{
    "name": "Juan Pérez Actualizado",
    "email": "'$(echo $USER_RESPONSE | jq -r .email)'",
    "phone": "+987654321"
}'

UPDATE_USER_RESPONSE=$(curl -s -X PUT "$API_GATEWAY_HOST/api/users/$USER_ID" \
  -H "Content-Type: application/json" \
  -d "$UPDATED_USER_DATA")

if ! echo "$UPDATE_USER_RESPONSE" | jq -e '.name == "Juan Pérez Actualizado"' > /dev/null 2>&1; then
    fail "Fallo al actualizar usuario. Respuesta: $UPDATE_USER_RESPONSE"
fi
echo "   ✅ Usuario actualizado. ID: $USER_ID"
echo "   Respuesta del servicio de usuarios:"
echo "$UPDATE_USER_RESPONSE" | jq .
echo "---"

## PRUEBA 4.2: Eliminación de Usuario (DELETE /api/users/{id})
echo "## 4.2. Eliminando usuario de prueba..."
# Crear un nuevo usuario para eliminar
DELETE_USER_DATA='{
    "name": "Usuario a Eliminar",
    "email": "delete-test-'"$RANDOM"'@example.com",
    "phone": "+111222333"
}'
DELETE_USER_RESPONSE=$(curl -s -X POST "$API_GATEWAY_HOST/api/users" \
  -H "Content-Type: application/json" \
  -d "$DELETE_USER_DATA")
DELETE_USER_ID=$(echo "$DELETE_USER_RESPONSE" | jq -r '.id')
echo "   ✅ Usuario a eliminar creado. ID: $DELETE_USER_ID"

# Eliminar el usuario
DELETE_RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_GATEWAY_HOST/api/users/$DELETE_USER_ID")
if [ "$DELETE_RESPONSE_CODE" -ne 204 ]; then
    fail "Fallo al eliminar usuario. Código de respuesta: $DELETE_RESPONSE_CODE"
fi
echo "   ✅ Usuario eliminado. ID: $DELETE_USER_ID"

# Verificar que el usuario no se puede obtener
GET_DELETED_USER_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_GATEWAY_HOST/api/users/$DELETE_USER_ID")
if [ "$GET_DELETED_USER_CODE" -ne 404 ]; then
    fail "El usuario eliminado todavía se puede obtener. Código de respuesta: $GET_DELETED_USER_CODE"
fi
echo "   ✅ El usuario eliminado no se puede obtener (404 esperado)."
echo "---"

## PRUEBA 4.3: Actualización de Producto (PUT /api/products/{id})
echo "## 4.3. Actualizando producto de prueba..."
UPDATED_PRODUCT_DATA='{
    "name": "Laptop Pro X",
    "description": "An even more powerful laptop",
    "price": 1599.99
}'
UPDATE_PRODUCT_RESPONSE=$(curl -s -X PUT "$API_GATEWAY_HOST/api/products/$PRODUCT_ID" \
  -H "Content-Type: application/json" \
  -d "$UPDATED_PRODUCT_DATA")

if ! echo "$UPDATE_PRODUCT_RESPONSE" | jq -e '.name == "Laptop Pro X"' > /dev/null 2>&1; then
    fail "Fallo al actualizar producto. Respuesta: $UPDATE_PRODUCT_RESPONSE"
fi
echo "   ✅ Producto actualizado. ID: $PRODUCT_ID"
echo "   Respuesta del servicio de productos:"
echo "$UPDATE_PRODUCT_RESPONSE" | jq .
echo "---"

## PRUEBA 4.4: Eliminación de Producto (DELETE /api/products/{id})
echo "## 4.4. Eliminando producto de prueba..."
# Crear un nuevo producto para eliminar
DELETE_PRODUCT_DATA='{
    "name": "Producto a Eliminar",
    "description": "Producto de prueba",
    "price": 9.99
}'
DELETE_PRODUCT_RESPONSE=$(curl -s -X POST "$API_GATEWAY_HOST/api/products" \
    -H "Content-Type: application/json" \
    -d "$DELETE_PRODUCT_DATA")
DELETE_PRODUCT_ID=$(echo "$DELETE_PRODUCT_RESPONSE" | jq -r '.id')
echo "   ✅ Producto a eliminar creado. ID: $DELETE_PRODUCT_ID"

# Eliminar el producto
DELETE_PRODUCT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$API_GATEWAY_HOST/api/products/$DELETE_PRODUCT_ID")
if [ "$DELETE_PRODUCT_CODE" -ne 204 ]; then
    fail "Fallo al eliminar producto. Código de respuesta: $DELETE_PRODUCT_CODE"
fi
echo "   ✅ Producto eliminado. ID: $DELETE_PRODUCT_ID"

# Verificar que el producto no se puede obtener
GET_DELETED_PRODUCT_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_GATEWAY_HOST/api/products/$DELETE_PRODUCT_ID")
if [ "$GET_DELETED_PRODUCT_CODE" -ne 404 ]; then
    fail "El producto eliminado todavía se puede obtener. Código de respuesta: $GET_DELETED_PRODUCT_CODE"
fi
echo "   ✅ El producto eliminado no se puede obtener (404 esperado)."
echo "---"

## PRUEBA 4.5: Obtener Orden Individual (GET /api/orders/{id})
echo "## 4.5. Obteniendo orden individual..."
GET_ORDER_RESPONSE=$(curl -s "$API_GATEWAY_HOST/api/orders/$ORDER_ID")
if ! echo "$GET_ORDER_RESPONSE" | jq -e '.id == '$ORDER_ID'' > /dev/null 2>&1; then
    fail "Fallo al obtener la orden. Respuesta: $GET_ORDER_RESPONSE"
fi
echo "   ✅ Orden obtenida. ID: $ORDER_ID"
echo "   Respuesta del servicio de órdenes:"
echo "$GET_ORDER_RESPONSE" | jq .
echo "---"

## PRUEBA 4.6: Intentar crear orden con usuario inválido
echo "## 4.6. Intentando crear orden con usuario inválido..."
INVALID_ORDER_DATA="{
    \"userId\": \"999999\",
    \"productId\": \"$PRODUCT_ID\",
    \"quantity\": 1
}"
INVALID_ORDER_RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_GATEWAY_HOST/api/orders" \
  -H "Content-Type: application/json" \
  -d "$INVALID_ORDER_DATA")

if [ "$INVALID_ORDER_RESPONSE_CODE" -ne 500 ] && [ "$INVALID_ORDER_RESPONSE_CODE" -ne 503 ]; then
    fail "La creación de orden con usuario inválido debería fallar con 500 o 503, pero se obtuvo $INVALID_ORDER_RESPONSE_CODE"
fi
echo "   ✅ La creación de orden con usuario inválido falló como se esperaba (Código: $INVALID_ORDER_RESPONSE_CODE)."
echo "---"

## PRUEBA 4.7: Intentar crear orden con producto inválido
echo "## 4.7. Intentando crear orden con producto inválido..."
INVALID_PRODUCT_ORDER_DATA="{
    \"userId\": \"$USER_ID\",
    \"productId\": \"999999\",
    \"quantity\": 1
}"
INVALID_PRODUCT_ORDER_RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API_GATEWAY_HOST/api/orders" \
  -H "Content-Type: application/json" \
  -d "$INVALID_PRODUCT_ORDER_DATA")

if [ "$INVALID_PRODUCT_ORDER_RESPONSE_CODE" -ne 500 ] && [ "$INVALID_PRODUCT_ORDER_RESPONSE_CODE" -ne 503 ]; then
    fail "La creación de orden con producto inválido debería fallar con 500 o 503, pero se obtuvo $INVALID_PRODUCT_ORDER_RESPONSE_CODE"
fi
echo "   ✅ La creación de orden con producto inválido falló como se esperaba (Código: $INVALID_PRODUCT_ORDER_RESPONSE_CODE)."
echo "---"

## PRUEBA 4.8: Intentar obtener una orden inexistente
echo "## 4.8. Intentando obtener una orden inexistente..."
GET_NON_EXISTENT_ORDER_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API_GATEWAY_HOST/api/orders/999999")
if [ "$GET_NON_EXISTENT_ORDER_CODE" -ne 404 ]; then
    fail "La obtención de una orden inexistente debería fallar con 404, pero se obtuvo $GET_NON_EXISTENT_ORDER_CODE"
fi
echo "   ✅ La obtención de una orden inexistente falló como se esperaba (Código: 404)."
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

## PRUEBA 5.1: Verificación de Circuit Breakers
echo "## 5.1. Verificación de estado de los Circuit Breakers en order-service..."
CB_STATE=$(curl -s http://localhost:8082/actuator/circuitbreakers | jq -r '.circuitBreakers.orderService.state')
if [ "$CB_STATE" == "OPEN" ]; then
    fail "El circuit breaker 'orderService' está en estado OPEN."
fi
echo "   ✅ Circuit breaker 'orderService' está en estado: $CB_STATE"
CB_STATE_USER=$(curl -s http://localhost:8082/actuator/circuitbreakers | jq -r '.circuitBreakers.userService.state')
if [ "$CB_STATE_USER" == "OPEN" ]; then
    fail "El circuit breaker 'userService' está en estado OPEN."
fi
echo "   ✅ Circuit breaker 'userService' está en estado: $CB_STATE_USER"
echo "---"

echo "🎉 Pruebas de integración completadas exitosamente."
exit 0