#!/bin/bash

echo "Iniciando microservicios Spring Cloud con Docker..."

# Limpiar contenedores previos
docker compose down

# Construir proyectos
echo "Construyendo proyectos Maven..."

cd discovery-server
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Error construyendo discovery-server"
    exit 1
fi
cd ..

cd user-service
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Error construyendo user-service"
    exit 1
fi
cd ..

cd order-service
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "Error construyendo order-service"
    exit 1
fi
cd ..

# Construir y ejecutar con Docker Compose
echo "Construyendo imágenes Docker..."
docker compose build --no-cache

echo "Iniciando servicios..."
docker compose up -d

echo "Esperando a que los servicios inicien..."
sleep 10

# Verificar estado
echo "Verificando estado de los contenedores:"
docker compose ps

# Mostrar logs iniciales
echo "Mostrando logs iniciales del discovery-server:"
docker compose logs discovery-server --tail=50

echo ""
echo "Si hay problemas, revisa los logs con: docker compose logs [servicio]"
echo ""
echo "URLs cuando estén disponibles:"
echo "- Eureka Dashboard: http://localhost:8761"
echo "- User Service: http://localhost:8081"
echo "- Order Service: http://localhost:8082"