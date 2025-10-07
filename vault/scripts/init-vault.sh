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

# Crear política para user-service
vault policy write user-service - <<EOF
path "kv/data/user-service" {
  capabilities = ["read"]
}
path "kv/metadata/user-service" {
  capabilities = ["list"]
}
EOF

# Crear token específico para user-service (opcional)
echo "Creating user-service token..."
vault token create -policy=user-service -display-name="user-service-token" -ttl=768h

echo "Vault configuration completed!"