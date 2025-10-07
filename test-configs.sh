#!/bin/bash

CONFIG_SERVER="http://config-server:8888"

echo "=== Testing Config Server ==="

# Test Config Server health
echo "1. Config Server Health:"
curl -s "$CONFIG_SERVER/actuator/health" | jq '.status'

# Test each service configuration
services=("discovery-server" "config-server" "api-gateway" "user-service" "order-service")

for service in "${services[@]}"; do
    echo ""
    echo "=== Testing $service ==="
    
    # Test YAML format
    echo "YAML format:"
    curl -s "$CONFIG_SERVER/$service/default.yml" | head -10
    
    # Test JSON format
    echo "JSON format:"
    curl -s "$CONFIG_SERVER/$service/default.json" | jq '.name, .profiles' 2>/dev/null || echo "JSON not available"
done

# Test global configuration
echo ""
echo "=== Testing Global Configuration ==="
curl -s "$CONFIG_SERVER/application/default.yml" | head -10