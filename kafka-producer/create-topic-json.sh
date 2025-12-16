#!/bin/bash

# Script pour créer un nouveau topic Kafka dédié aux transactions JSON

echo "Création du topic TransactionsJSON..."

docker exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic TransactionsJSON \
  --partitions 1 \
  --replication-factor 1 \
  --if-not-exists

if [ $? -eq 0 ]; then
    echo "✓ Topic TransactionsJSON créé avec succès!"
    echo ""
    echo "Vous pouvez maintenant :"
    echo "1. Modifier le producer JSON pour utiliser le topic 'TransactionsJSON'"
    echo "2. Créer une nouvelle spec d'ingestion pour ce topic"
else
    echo "✗ Erreur lors de la création du topic"
    exit 1
fi



