#!/bin/bash

# Script pour vérifier le statut d'une ingestion Kafka dans Druid

DRUID_ROUTER_URL="${DRUID_ROUTER_URL:-http://localhost:8888}"
SUPERVISOR_ID="${1:-Transactions}"

echo "Vérification du statut du supervisor: $SUPERVISOR_ID"
echo "URL: $DRUID_ROUTER_URL/druid/indexer/v1/supervisor/$SUPERVISOR_ID/status"
echo ""

response=$(curl -s "$DRUID_ROUTER_URL/druid/indexer/v1/supervisor/$SUPERVISOR_ID/status")

if [ $? -eq 0 ]; then
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
else
    echo "Erreur: Impossible de se connecter à Druid"
    exit 1
fi



