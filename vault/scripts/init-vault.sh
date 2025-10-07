#!/bin/bash

set -e

echo "Waiting for Vault to start..."
sleep 5

export VAULT_ADDR="http://localhost:8200"
export VAULT_TOKEN="myroot"

# Esperar a que Vault esté listo
while ! vault status > /dev/null 2>&1; do
  echo "Waiting for Vault to be ready..."
  sleep 2
done

echo "Vault is ready! Configuring secrets..."

# Habilitar KV v2 en path 'kv' (si no existe)
if ! vault secrets list | grep -q "kv/"; then
    vault secrets enable -path=kv -version=2 kv
fi

# Crear secrets para user-service con TODAS las variables necesarias
vault kv put kv/user-service/data \
  database.url="jdbc:h2:mem:testdb" \
  database.username="sa" \
  database.password="sa" \
  encryption.key="user-enc-key-456" \
  h2.console.password="console-pass-789"

echo "Secrets created:"
vault kv get kv/user-service

# Crear secrets para api-gateway
echo "Creating secrets for api-gateway..."
vault kv put kv/api-gateway/data \
  rate.limit.user-service=10 \
  rate.limit.order-service=15 \
  circuitbreaker.failure-threshold=50 \
  retry.max-attempts=3


# Crear política para user-service
vault policy write user-service - <<EOF
path "kv/data/user-service" {
  capabilities = ["read"]
}
path "kv/metadata/user-service" {
  capabilities = ["list"]
}
EOF

# Crear política para api-gateway
vault policy write api-gateway - <<EOF
path "kv/data/api-gateway" {
  capabilities = ["read"]
}

path "kv/metadata/api-gateway" {
  capabilities = ["list", "read"]
}

# Permiso para leer configuraciones de otros servicios (opcional)
path "kv/data/user-service" {
  capabilities = ["read"]
}

path "kv/data/order-service" {
  capabilities = ["read"]
}
EOF

# Crear token específico para user-service (opcional)
echo "Creating user-service token..."
vault token create -policy=user-service -display-name="user-service-token" -ttl=768h

# Crear token para api-gateway
echo "Creating api-gateway-token..."
vault token create -policy=api-gateway -display-name="api-gateway-token" -ttl=768h

echo "Vault configuration completed!"