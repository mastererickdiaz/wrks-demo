#!/bin/bash

echo "Iniciando microservicios Spring Cloud con Docker..."

# Verificar Docker
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker no está en ejecución"
    exit 1
fi

# Limpiar contenedores previos
echo "Deteniendo servicios anteriores..."
docker compose down -v

# Ejecutar script de construcción
echo "Construyendo proyectos Maven..."
./build-all.sh
if [ $? -ne 0 ]; then
    echo "Error en la construcción de proyectos"
    exit 1
fi

# Construir y ejecutar con Docker Compose
echo "Construyendo imágenes Docker..."
docker compose build --no-cache

echo "Iniciando servicios..."
docker compose up -d

echo "Esperando a que los servicios inicien..."
echo "Esto puede tomar varios minutos..."

# Función para verificar salud del servicio
check_health() {
    local service=$1
    local port=$2
    local max_attempts=3
    local attempt=1

    echo "Verificando $service en puerto $port..."
    while [ $attempt -le $max_attempts ]; do
        curl -s http://localhost:$port/actuator/health | grep "UP" > /dev/null
        if [ $? -eq 0 ]; then
            echo "✅ $service está listo"
            return 0
        fi
        echo "⏳ Intento $attempt/$max_attempts - $service aún no está listo..."
        attempt=$((attempt + 1))
        sleep 5
    done
    echo "❌ $service no respondió después de $max_attempts intentos"
    return 1
}

# Verificar servicios en orden
check_health "Config Server" 8888
check_health "Discovery Server" 8761
check_health "API Gateway" 8080
check_health "User Service" 8081
check_health "Order Service" 8082

# Verificar estado final
echo "Estado final de los contenedores:"
docker compose ps

# Mostrar URLs de acceso
echo ""
echo "🌟 Servicios disponibles en:"
echo "├─ API Gateway y Swagger UI: http://localhost:8080/swagger-ui.html"
echo "├─ Eureka Dashboard: http://localhost:8761"
echo "├─ Config Server: http://localhost:8888"
echo "├─ Vault UI: http://localhost:8200"
echo "└─ Redis está ejecutándose en el puerto 6379"
echo ""
docker compose logs discovery-server --tail=50

echo ""
echo "Si hay problemas, revisa los logs con: docker compose logs [servicio]"
echo ""
echo "URLs cuando estén disponibles:"
echo "- Eureka Dashboard: http://localhost:8761"
echo "- User Service: http://localhost:8081"
echo "- Order Service: http://localhost:8082"