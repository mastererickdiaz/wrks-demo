#!/bin/bash

# ==============================================================================
# Script para probar el endpoint interno del Order Service con Firmas HTTP
# ==============================================================================

set -e

# --- Configuración de la Petición ---
HOST="localhost:8080"
METHOD="GET"
PATH_URL="/api/internal/orders/user/1"
PRIVATE_KEY_FILE="./security-keys/private_key.pem"
KEY_ID="order-service-key"

# --- Cabeceras ---
REQUEST_DATE=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
REQUEST_ID=$(uuidgen)
APP_CODE="TESTCLIENT"
APP_NAME="Test Client"

echo "🚀 Preparando petición para: $METHOD $HOST$PATH_URL"
echo "   - Key ID: $KEY_ID"
echo "   - Request ID: $REQUEST_ID"
echo "   - Request Date: $REQUEST_DATE"

# 1. Definir los componentes que se van a firmar (Signature-Input)
CREATED_TIMESTAMP=$(date +%s)
SIGNATURE_INPUT="sig1=(\"@method\" \"@path\" \"x-request-id\" \"x-request-date\" \"app-code\");created=$CREATED_TIMESTAMP;keyId=\"$KEY_ID\""

echo "   - Signature-Input: $SIGNATURE_INPUT"

# 2. Construir la "cadena base" para la firma
# IMPORTANTE: Usar printf en lugar de echo para manejar correctamente los saltos de línea
# IMPORTANTE: NO hay salto de línea después del último componente (@signature-params)

# Extraer solo la parte de parámetros (sin "sig1=")
SIGNATURE_PARAMS="${SIGNATURE_INPUT#sig1=}"

# Construir la base string correctamente
SIGNATURE_BASE=$(printf '"@method": %s\n"@path": %s\n"x-request-id": %s\n"x-request-date": %s\n"app-code": %s\n"@signature-params": %s' \
  "$METHOD" \
  "$PATH_URL" \
  "$REQUEST_ID" \
  "$REQUEST_DATE" \
  "$APP_CODE" \
  "$SIGNATURE_PARAMS")

# Debug: Mostrar la cadena base (opcional, comentar en producción)
echo ""
echo "=== CADENA BASE PARA FIRMAR ==="
echo "$SIGNATURE_BASE"
echo "=== FIN CADENA BASE ==="
echo ""

# 3. Firmar la cadena base con la clave privada usando OpenSSL
SIGNATURE=$(printf '%s' "$SIGNATURE_BASE" | openssl dgst -sha256 -sign "$PRIVATE_KEY_FILE" | base64 -w 0)

echo "   - Signature (Base64): $SIGNATURE"
echo "--------------------------------------------------"

# 4. Ejecutar la petición con curl
curl --location --request "$METHOD" "http://$HOST$PATH_URL" \
--header "x-request-id: $REQUEST_ID" \
--header "x-request-date: $REQUEST_DATE" \
--header "app-code: $APP_CODE" \
--header "app-name: $APP_NAME" \
--header "Signature-Input: $SIGNATURE_INPUT" \
--header "Signature: sig1=:$SIGNATURE:" \
--verbose

echo -e "\n\n✅ Petición completada."