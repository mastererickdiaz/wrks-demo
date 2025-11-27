#!/bin/bash

# Detener la ejecución si algún comando falla
set -e

echo "Waiting for Vault to start..."
sleep 5

# ============================================
# CONFIGURACIÓN INICIAL DE VAULT
# ============================================

# Variables de entorno para conectarse a Vault
export VAULT_ADDR="http://vault:8200"  # URL del servidor Vault
export VAULT_TOKEN="myroot"             # Token root para autenticación inicial

# ============================================
# VERIFICAR QUE VAULT ESTÉ DISPONIBLE
# ============================================

# Bucle que espera hasta que Vault responda correctamente
while ! vault status > /dev/null 2>&1; do
  echo "Waiting for Vault to be ready..."
  sleep 2
done

echo "Vault is ready! Configuring secrets..."

# ============================================
# HABILITAR MOTOR DE SECRETOS KV V2
# ============================================

# Verificar si el path 'kv' ya existe, si no, habilitarlo
# KV v2 permite versionado de secretos
if ! vault secrets list | grep -q "kv/"; then
    vault secrets enable -path=kv -version=2 kv
fi

# ============================================
# SECRETOS PARA APLICACIONES
# ============================================

echo "Creating placeholder secrets for 'application' contexts..."
vault kv put kv/application/docker placeholder=placeholder
vault kv put kv/application placeholder=placeholder

# ============================================
# SECRETOS PARA ORDER-SERVICE
# ============================================

echo "Writing secrets for order-service..."

# Almacenar claves y mensaje para order-service
vault kv put kv/order-service/docker \
  privateKey=@/vault/security-keys/private_key.pem \
  publicKey=@/vault/security-keys/public_key.pem \
  processing.message="[FROM VAULT] Processing new order..."
echo "✅ Secrets for order-service stored in Vault at kv/order-service/docker"

# ============================================
# SECRETOS PARA USER-SERVICE
# ============================================

echo "Creating secrets for user-service..."
# Almacenar todas las configuraciones necesarias para user-service
vault kv put kv/user-service/docker \
  database.url="jdbc:h2:mem:testdb" \
  database.username="sa" \
  database.password="sa" \
  encryption.key="user-enc-key-456" \
  h2.console.password="console-pass-789"
echo "✅ Secrets for user-service stored in Vault at kv/user-service/docker"

# ============================================
# SECRETOS PARA API-GATEWAY
# ============================================

echo "Creating secrets for api-gateway..."

# Configuraciones de rate limiting, circuit breaker y reintentos
vault kv put kv/api-gateway/docker \
  rate.limit.user-service=10 \
  rate.limit.order-service=15 \
  circuitbreaker.failure-threshold=50 \
  retry.max-attempts=3
echo "✅ Secrets for api-gateway stored in Vault at kv/api-gateway/docker"

# ============================================
# VERIFICAR SECRETOS ALMACENADOS
# ============================================

echo "Secrets created. Verifying..."
vault kv get kv/user-service/docker    # Mostrar secretos de user-service
vault kv get kv/order-service/docker   # Mostrar secretos de order-service
vault kv get kv/api-gateway/docker   # Mostrar secretos de api-gateway

# ============================================
# POLÍTICAS DE ACCESO - USER-SERVICE
# ============================================

# Crear política que define qué puede leer user-service
vault policy write user-service - <<EOF
# Permitir a user-service leer su propia clave pública
path "kv/data/user-service/docker" {
  capabilities = ["read"]
}
path "kv/metadata/user-service/docker" {
  capabilities = ["list"]
}

# Permitir a user-service leer la clave pública de order-service
# (necesario para verificar firmas HTTP de peticiones entrantes)
path "kv/data/order-service/docker" {
  capabilities = ["read"]
}
path "kv/metadata/order-service/docker" {
  capabilities = ["list"]
}
EOF

# ============================================
# POLÍTICAS DE ACCESO - API-GATEWAY
# ============================================

# Crear política para el API Gateway
vault policy write api-gateway - <<EOF
# Leer configuraciones propias del gateway
path "kv/data/api-gateway/docker" {
  capabilities = ["read"]
}

path "kv/metadata/api-gateway/docker" {
  capabilities = ["list", "read"]
}

# Permitir lectura de configuraciones de servicios backend
# (necesario para enrutamiento y configuración dinámica)
path "kv/data/user-service/docker" {
  capabilities = ["read"]
}

path "kv/data/order-service/docker" {
  capabilities = ["read"]
}
EOF

# ============================================
# POLÍTICAS DE ACCESO - ORDER-SERVICE
# ============================================

# Crear política para order-service (más restrictiva)
vault policy write order-service - <<EOF
# Solo puede leer sus propios secretos
path "kv/data/order-service/docker" {
  capabilities = ["read"]
}
path "kv/metadata/order-service/docker" {
  capabilities = ["list"]
}
EOF

# ============================================
# GENERAR TOKENS DE ACCESO
# ============================================

# Token para api-gateway (válido por 768 horas = 32 días)
echo "Creating api-gateway-token..."
vault token create -policy=api-gateway -display-name="api-gateway-token" -ttl=768h

# Token para user-service (válido por 768 horas = 32 días)
echo "Creating user-service token..."
vault token create -policy=user-service -display-name="user-service-token" -ttl=768h

# Token para order-service (válido por 768 horas = 32 días)
echo "Creating order-service-token..."
vault token create -policy=order-service -display-name="order-service-token" -ttl=768h

echo "Vault configuration completed!"