#!/bin/bash

echo "Construyendo todos los microservicios..."

# Construir discovery-server
echo "Construyendo discovery-server..."
cd discovery-server
mvn clean package -DskipTests
cd ..

# Construir user-service
echo "Construyendo user-service..."
cd user-service
mvn clean package -DskipTests
cd ..

# Construir order-service
echo "Construyendo order-service..."
cd order-service
mvn clean package -DskipTests
cd ..

echo "Construcción completada!"