#!/bin/bash

set -e

echo "🔐 Generando claves de seguridad con OpenSSL..."

KEYS_DIR="./security-keys"
mkdir -p $KEYS_DIR

# Generar private key
openssl genrsa -out $KEYS_DIR/private_key.pem 2048

# Extraer public key
openssl rsa -in $KEYS_DIR/private_key.pem -pubout -out $KEYS_DIR/public_key.pem

# Generar un certificado autofirmado
openssl req -new -x509 -key $KEYS_DIR/private_key.pem -out $KEYS_DIR/certificate.pem -days 365 -subj "/CN=localhost"

# Generar keystore para SSL
openssl pkcs12 -export -in $KEYS_DIR/certificate.pem -inkey $KEYS_DIR/private_key.pem \
    -out $KEYS_DIR/keystore.p12 -name "microservice" -passout pass:changeit

echo "✅ Claves de seguridad generadas:"
echo "   Private Key: $KEYS_DIR/private_key.pem"
echo "   Public Key:  $KEYS_DIR/public_key.pem"
echo "   Certificate: $KEYS_DIR/certificate.pem"
echo "   Keystore:    $KEYS_DIR/keystore.p12"

# Verificar claves
echo "🔍 Verificando claves..."
openssl rsa -in $KEYS_DIR/private_key.pem -check -noout
openssl rsa -pubin -in $KEYS_DIR/public_key.pem -inform PEM -text -noout | head -5
openssl x509 -in $KEYS_DIR/certificate.pem -text -noout | head -10