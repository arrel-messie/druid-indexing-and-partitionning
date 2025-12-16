#!/bin/bash

# Script pour soumettre une tâche d'ingestion Kafka à Druid

DRUID_ROUTER_URL="${DRUID_ROUTER_URL:-http://localhost:8888}"
SPEC_FILE="${1:-kafka-transactions-ingestion-spec.json}"

if [ ! -f "$SPEC_FILE" ]; then
    echo "Erreur: Le fichier de spec $SPEC_FILE n'existe pas"
    exit 1
fi

echo "Soumission de la spec d'ingestion à Druid..."
echo "URL: $DRUID_ROUTER_URL/druid/indexer/v1/supervisor"
echo "Fichier: $SPEC_FILE"
echo ""

response=$(curl -s -w "\n%{http_code}" -X POST \
  "$DRUID_ROUTER_URL/druid/indexer/v1/supervisor" \
  -H "Content-Type: application/json" \
  -d @"$SPEC_FILE")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" -eq 200 ] || [ "$http_code" -eq 201 ]; then
    echo "✓ Ingestion démarrée avec succès!"
    echo ""
    echo "Réponse:"
    echo "$body" | jq '.' 2>/dev/null || echo "$body"
    echo ""
    echo "Pour vérifier le statut:"
    echo "  curl $DRUID_ROUTER_URL/druid/indexer/v1/supervisor/Transactions/status"
else
    echo "✗ Erreur lors de la soumission (HTTP $http_code)"
    echo ""
    echo "Réponse:"
    echo "$body" | jq '.' 2>/dev/null || echo "$body"
    exit 1
fi



