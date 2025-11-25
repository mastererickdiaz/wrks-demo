#!/bin/bash

# ==============================================================================
# Configuración
# ==============================================================================
# Credenciales de Eureka (usadas para healthchecks directos)
EUREKA_USER="eureka-user"
EUREKA_PASS="eureka-pass"
MAX_ATTEMPTS=60 # Intentos máximos de verificación de salud
WAIT_INTERVAL=5   # Intervalo de espera en segundos

# Puertos y servicios para verificación de Actuator
declare -A SERVICE_HEALTH_PORTS
SERVICE_HEALTH_PORTS=(
    ["Config Server"]="8888"
    ["Discovery Server"]="8761"
    ["API Gateway"]="8080"
    ["User Service"]="8081"
    ["Order Service"]="8082"
    ["Product Service"]="8083"
    ["Vault"]="8200" # Se añade Vault para verificación
)

# ==============================================================================
# Funciones
# ==============================================================================

# Función: Terminar con mensaje de error
fail() {
    echo -e "\n\n❌ ERROR: $1" >&2
    exit 1
}

# Función: Esperar a que el Actuator de un servicio responda UP
wait_for_service_ready() {
    local service=$1
    local port=$2
    local attempt=1
    
    if [ "$service" == "Vault" ]; then
        echo "   Verificando Vault (estado del contenedor)..."
        while [ $attempt -le $MAX_ATTEMPTS ]; do
            if docker compose ps vault | grep -q "(healthy)"; then
                echo "   ✅ Vault está listo y saludable."
                return 0
            fi
            echo "   ⏳ Intento $attempt/$MAX_ATTEMPTS - Vault aún no está listo. Esperando $WAIT_INTERVAL s..."
            attempt=$((attempt + 1))
            sleep $WAIT_INTERVAL
        done
        fail "Vault no alcanzó el estado saludable en el tiempo límite."
    fi

    local health_url="http://localhost:$port/actuator/health"

    # Manejo especial para Discovery Server (requiere autenticación)
    if [ "$service" == "Discovery Server" ]; then
        health_url="http://$EUREKA_USER:$EUREKA_PASS@localhost:$port/actuator/health"
    fi
    
    echo "   Verificando $service ($health_url)..."
    
    while [ $attempt -le $MAX_ATTEMPTS ]; do
        # Verificar que el estado global sea UP (usando jq, si está disponible)
        if command -v jq >/dev/null 2>&1; then
            HEALTH_STATUS=$(curl -s $health_url | jq -r '.status')
            if [ "$HEALTH_STATUS" == "UP" ]; then
                echo "   ✅ $service está listo. (Estado: UP)"
                return 0
            fi
        else
            # Fallback si jq no está instalado
            curl -s $health_url | grep -q "UP"
            if [ $? -eq 0 ]; then
                 echo "   ✅ $service está listo."
                 return 0
            fi
        fi

        echo "   ⏳ Intento $attempt/$MAX_ATTEMPTS - $service aún no está listo. Esperando $WAIT_INTERVAL s..."
        attempt=$((attempt + 1))
        sleep $WAIT_INTERVAL
    done
    
    fail "$service no respondió en el tiempo límite ($((MAX_ATTEMPTS * WAIT_INTERVAL)) segundos)."
}


# ==============================================================================
# Ejecución Principal
# ==============================================================================

echo "🚀 Iniciando proceso de despliegue y verificación secuencial..."

# 1. Verificar requisitos
if ! docker info > /dev/null 2>&1; then
    fail "Docker no está en ejecución. Por favor, inícialo."
fi

# 2. Limpiar contenedores previos
echo -e "\n---"
echo "🧹 Deteniendo y eliminando servicios anteriores..."
docker compose down -v

# 3. Ejecutar script de construcción (Asumimos que build-all.sh existe)
echo -e "\n---"
echo "🛠️ Construyendo proyectos Maven..."
./build-all.sh
if [ $? -ne 0 ]; then
    fail "La construcción de proyectos Maven falló."
fi

# 4. Construir imágenes Docker
echo -e "\n---"
echo "📦 Construyendo imágenes Docker..."
docker compose build --no-cache

# 5. Iniciar y esperar servicios en orden secuencial

echo -e "\n---"
echo "⏳ Iniciando servicios base (Redis y Vault)..."
# Iniciar Redis y Vault (no dependen de nadie más que del host)
docker compose up -d redis vault
wait_for_service_ready "Vault" 8200 # Vault es crucial para la configuración de los demás servicios

echo -e "\n---"
echo "⏳ Iniciando servicios de infraestructura (Discovery y Config)..."
# Iniciar Discovery Server y Config Server (dependen de Redis/Vault si Vault fuera usado por Config Server)
docker compose up -d discovery-server config-server

wait_for_service_ready "Discovery Server" 8761
wait_for_service_ready "Config Server" 8888

echo -e "\n---"
echo "⏳ Iniciando servicios de aplicación (API Gateway, User, Order)..."
# Iniciar API Gateway y servicios de negocio
# NOTA: Agregamos una variable de entorno al API Gateway para arreglar el error 404 de las métricas.
docker compose up -d api-gateway user-service order-service product-service

# Verificación de salud secuencial para los servicios de aplicación
wait_for_service_ready "User Service" 8081
wait_for_service_ready "Order Service" 8082
wait_for_service_ready "Product Service" 8083
wait_for_service_ready "API Gateway" 8080

echo -e "\n---"
echo "✅ Todos los microservicios están listos y saludables."

# 6. Mostrar estado y URLs
echo -e "\n📋 Estado de los contenedores:"
docker compose ps

echo -e "\n🌟 Servicios disponibles en:"
echo "├─ API Gateway y Swagger UI: http://localhost:8080/swagger-ui.html"
echo "├─ Eureka Dashboard: http://$EUREKA_USER:$EUREKA_PASS@localhost:8761 (usa credenciales: $EUREKA_USER/$EUREKA_PASS)"
echo "├─ Config Server: http://localhost:8888"
echo "└─ Vault UI: http://localhost:8200"

echo -e "\n Últimos logs del Discovery Server:"
docker compose logs discovery-server --tail=50

echo -e "\n---"
echo "🎉 Despliegue secuencial completado con éxito."
exit 0