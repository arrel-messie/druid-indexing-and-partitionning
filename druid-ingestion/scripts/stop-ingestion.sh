#!/bin/bash

# Script pour arrêter une ingestion Kafka dans Druid

DRUID_ROUTER_URL="${DRUID_ROUTER_URL:-http://localhost:8888}"
SUPERVISOR_ID="${1:-Transactions}"

echo "Arrêt du supervisor: $SUPERVISOR_ID"
echo "URL: $DRUID_ROUTER_URL/druid/indexer/v1/supervisor/$SUPERVISOR_ID/terminate"
echo ""

response=$(curl -s -X POST "$DRUID_ROUTER_URL/druid/indexer/v1/supervisor/$SUPERVISOR_ID/terminate")

if [ $? -eq 0 ]; then
    echo "✓ Supervisor arrêté avec succès!"
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
else
    echo "✗ Erreur lors de l'arrêt"
    exit 1
fi



