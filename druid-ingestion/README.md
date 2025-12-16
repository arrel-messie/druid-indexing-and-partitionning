# Guide d'Ingestion Kafka dans Druid
## Topic: Transactions

Ce guide explique comment connecter le topic Kafka "Transactions" à Druid pour l'ingestion en temps réel.

## Prérequis

- Druid cluster en cours d'exécution (port 8888 pour Router)
- Kafka en cours d'exécution (port 9092)
- Schema Registry en cours d'exécution (port 8085)
- Topic "Transactions" créé dans Kafka
- Schéma Protobuf enregistré dans Schema Registry

## Méthode 1 : Via l'Interface Web Druid (Recommandé)

### Étape 1 : Accéder à l'interface Druid

1. Ouvrez votre navigateur et allez à : **http://localhost:8888**
2. Cliquez sur **"Load data"** dans le menu supérieur
3. Sélectionnez **"Apache Kafka"** comme source de données

### Étape 2 : Configuration de la connexion Kafka

1. **Kafka bootstrap servers** : `localhost:9092`
2. **Topic** : `Transactions`
3. **Consumer group** : `druid-transactions-consumer` (ou laissez la valeur par défaut)

### Étape 3 : Configuration du parsing (Protobuf)

**Important** : Druid ne supporte pas nativement Protobuf avec Schema Registry. Vous avez deux options :

#### Option A : Utiliser un parser JSON (si vous convertissez en JSON)

Si vous modifiez votre producer pour envoyer en JSON au lieu de Protobuf :

1. Sélectionnez **"JSON"** comme format
2. Configurez le schéma JSON

#### Option B : Utiliser un parser personnalisé (Recommandé)

Pour utiliser Protobuf directement, vous devez :

1. Utiliser l'API Druid pour soumettre une spec d'ingestion (voir Méthode 2)
2. Ou créer un parser personnalisé

### Étape 4 : Configuration du schéma de données

Configurez les colonnes basées sur votre message Protobuf :

- **transaction_id** (STRING)
- **montant** (DOUBLE)
- **methode** (STRING) - Enum converti en string
- **commande_id** (STRING)
- **timestamp** (LONG) - Utilisé comme colonne de temps

### Étape 5 : Configuration de la granularité

- **Segment granularity** : `HOUR` (recommandé pour les transactions)
- **Query granularity** : `MINUTE` (pour des requêtes précises)

### Étape 6 : Lancer l'ingestion

1. Cliquez sur **"Next"** pour passer en revue la configuration
2. Cliquez sur **"Submit"** pour lancer la tâche d'ingestion
3. Surveillez la progression dans l'onglet **"Tasks"**

## Méthode 2 : Via l'API Druid (Pour Protobuf)

### Étape 1 : Préparer la spec d'ingestion

Utilisez le fichier `kafka-transactions-ingestion-spec.json` fourni dans ce dossier.

### Étape 2 : Soumettre la spec via l'API

```bash
curl -X POST http://localhost:8888/druid/indexer/v1/supervisor \
  -H "Content-Type: application/json" \
  -d @kafka-transactions-ingestion-spec.json
```

### Étape 3 : Vérifier le statut

```bash
# Lister les supervisors actifs
curl http://localhost:8888/druid/indexer/v1/supervisor

# Vérifier le statut d'un supervisor spécifique
curl http://localhost:8888/druid/indexer/v1/supervisor/transactions/status
```

## Méthode 3 : Conversion Protobuf → JSON (Solution Alternative)

Si vous préférez utiliser l'interface web, vous pouvez modifier votre producer pour convertir les messages Protobuf en JSON avant l'envoi.

### Avantages :
- Interface web Druid entièrement fonctionnelle
- Pas besoin de parser personnalisé
- Plus simple à déboguer

### Inconvénients :
- Perte de la validation de schéma Protobuf
- Taille de message légèrement plus grande

## Vérification de l'Ingestion

### 1. Vérifier les données ingérées

```sql
SELECT COUNT(*) 
FROM "Transactions"
WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' HOUR
```

### 2. Vérifier les segments créés

Dans l'interface Druid :
- Allez dans **"Datasources"**
- Cliquez sur **"Transactions"**
- Vérifiez l'onglet **"Segments"**

### 3. Tester une requête

```sql
SELECT 
  transaction_id,
  montant,
  methode,
  commande_id,
  __time
FROM "Transactions"
WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' DAY
ORDER BY __time DESC
LIMIT 10
```

## Configuration Avancée

### Ajuster les paramètres de performance

Modifiez la spec d'ingestion pour ajuster :
- `taskCount` : Nombre de tâches d'ingestion parallèles
- `replicas` : Nombre de réplicas pour la haute disponibilité
- `taskDuration` : Durée de chaque tâche

### Monitoring

- **Supervisor Status** : http://localhost:8888/druid/indexer/v1/supervisor/transactions/status
- **Tasks** : http://localhost:8888/druid/indexer/v1/tasks
- **Metrics** : http://localhost:8888/status/metrics

## Dépannage

### Problème : Aucune donnée n'est ingérée

1. Vérifiez que Kafka contient des messages :
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic Transactions --from-beginning
   ```

2. Vérifiez les logs du MiddleManager :
   ```bash
   docker logs middlemanager
   ```

3. Vérifiez le statut du supervisor :
   ```bash
   curl http://localhost:8888/druid/indexer/v1/supervisor/transactions/status
   ```

### Problème : Erreur de parsing Protobuf

- Druid ne supporte pas nativement Protobuf
- Utilisez la méthode de conversion JSON ou un parser personnalisé
- Vérifiez que le schéma est correctement enregistré dans Schema Registry

### Problème : Connexion à Kafka échoue

1. Vérifiez que Kafka est accessible depuis Druid :
   ```bash
   docker exec middlemanager ping kafka
   ```

2. Vérifiez la configuration réseau dans docker-compose.yml
3. Assurez-vous que les deux services sont sur le même réseau

## Arrêter l'Ingestion

```bash
curl -X POST http://localhost:8888/druid/indexer/v1/supervisor/transactions/terminate
```

## Ressources

- [Documentation Druid Kafka Ingestion](https://druid.apache.org/docs/latest/ingestion/native-batch.html#kafka)
- [Druid Supervisor API](https://druid.apache.org/docs/latest/operations/api-reference.html#supervisors)
- [Druid Query Guide](https://druid.apache.org/docs/latest/querying/querying.html)



