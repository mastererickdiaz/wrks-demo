#!/bin/bash

echo "=== Diagnóstico del Sistema ==="

echo "1. Verificando contenedores:"
docker compose ps -a

echo ""
echo "2. Logs del discovery-server:"
docker compose logs discovery-server --tail=20

echo ""
echo "3. Verificando imágenes:"
docker images | grep wrks-demo

echo ""
echo "4. Verificando red:"
docker network ls | grep microservices

echo ""
echo "5. Verificando recursos del sistema:"
docker system df