# Guide de test de l'ingestion JSON

## Problème résolu

L'erreur `Invalid UTF-32 character` se produisait car :
- La spec d'ingestion était configurée pour JSON
- Le topic contenait encore des données Protobuf binaires de l'ancien producer
- Druid essayait de lire du binaire comme du texte UTF-8

## Solution

La spec a été modifiée pour utiliser `useEarliestOffset: false`, ce qui permet d'ignorer les anciens messages Protobuf et de ne lire que les nouveaux messages JSON.

## Étapes pour tester

### 1. Démarrer le producer JSON

```bash
cd kafka-producer
./run-json.sh
```

Le producer va commencer à envoyer des messages JSON toutes les 30 secondes.

### 2. Attendre quelques messages

Laissez le producer envoyer 2-3 messages (environ 1 minute) pour s'assurer qu'il y a des données JSON dans le topic.

### 3. Lancer l'ingestion Druid

```bash
cd ../druid-ingestion
./scripts/submit-ingestion.sh kafka-transactions-json-producer-spec.json
```

### 4. Vérifier le statut

```bash
./scripts/check-status.sh Transactions
```

Le statut devrait passer à `RUNNING` et `healthy: true` après quelques secondes.

## Vérification

Pour vérifier que les données sont bien ingérées :

1. **Vérifier le statut de l'ingestion** :
   ```bash
   curl http://localhost:8888/druid/indexer/v1/supervisor/Transactions/status | jq
   ```

2. **Vérifier les données dans Druid** :
   - Accéder à http://localhost:8888
   - Aller dans "Query"
   - Exécuter une requête SQL :
     ```sql
     SELECT * FROM "Transactions" LIMIT 10
     ```

## Structure des données JSON

Les messages envoyés ont cette structure :

```json
{
  "transaction_id": "uuid",
  "montant": 123.45,
  "methode": "CARTE",
  "commande_id": "CMD-XXXXXXXX",
  "timestamp": 1234567890123
}
```

## Notes importantes

- **Consumer Group** : La spec utilise maintenant `druid-transactions-json-consumer` pour éviter les conflits
- **Offset** : `useEarliestOffset: false` signifie que Druid ne lit que les nouveaux messages (depuis maintenant)
- **Anciennes données** : Les anciens messages Protobuf dans le topic seront ignorés

## En cas de problème

Si l'ingestion échoue encore :

1. Vérifier que le producer JSON envoie bien des données :
   ```bash
   # Dans AKHQ (http://localhost:8084), vérifier le topic Transactions
   # Les messages doivent être en JSON, pas en binaire
   ```

2. Vérifier les logs Druid :
   ```bash
   docker logs middlemanager | tail -50
   ```

3. Réinitialiser le consumer group si nécessaire :
   - Changer `consumerGroupId` dans la spec
   - Ou supprimer le topic et le recréer



