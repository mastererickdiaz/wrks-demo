#!/bin/bash

echo "Construyendo todos los microservicios..."

# Función para construir un servicio
build_service() {
    local service_name=$1
    echo "Construyendo ${service_name}..."
    cd ${service_name}
    mvn clean package -DskipTests
    local result=$?
    cd ..
    if [ $result -ne 0 ]; then
        echo "Error construyendo ${service_name}"
        exit 1
    fi
    echo "${service_name} construido exitosamente"
    echo "-------------------------"
}

# Construir servicios de infraestructura primero
build_service "config-server"
build_service "discovery-server"
build_service "api-gateway"

# Construir servicios de negocio
build_service "user-service"
build_service "order-service"

echo "¡Construcción completada exitosamente!"