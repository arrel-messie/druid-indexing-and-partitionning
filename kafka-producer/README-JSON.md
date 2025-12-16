# Transaction JSON Producer

Ce producer envoie des transactions au format JSON vers le topic Kafka "Transactions".

## Caractéristiques

- **Format**: JSON (au lieu de Protobuf)
- **Topic**: Transactions
- **Intervalle d'envoi**: Configurable (par défaut: 30 secondes)
- **Données**: Transactions avec `transaction_id`, `montant`, `methode`, `commande_id`, `timestamp`

## Structure JSON

Les messages envoyés ont la structure suivante :

```json
{
  "transaction_id": "uuid",
  "montant": 123.45,
  "methode": "CARTE|VIREMENT|PAYPAL|CRYPTO",
  "commande_id": "CMD-XXXXXXXX",
  "timestamp": 1234567890123
}
```

## Configuration

Le producer utilise le fichier `src/main/resources/application.properties` pour la configuration. Les paramètres disponibles sont :

```properties
# Kafka Configuration
kafka.bootstrap.servers=localhost:9092
kafka.client.id=transaction-json-producer
kafka.topic.name=Transactions

# Producer Settings
producer.send.interval.seconds=30
producer.send.count=-1  # -1 = infinite
producer.random.data.enabled=true
producer.acks=all
producer.retries=3
producer.enable.idempotence=true

# Data Generation
data.montant.min=10.0
data.montant.max=1000.0
data.commande.id.prefix=CMD-
```

## Utilisation

### Option 1: Script de lancement

```bash
./run-json.sh
```

### Option 2: Compilation et exécution manuelle

```bash
# Compiler le projet
mvn clean package -DskipTests

# Exécuter le producer JSON
java -cp target/kafka-producer-1.0.0.jar \
    com.kafka.injector.producer.TransactionJsonProducer
```

### Option 3: Avec Maven

```bash
mvn exec:java -Dexec.mainClass="com.kafka.injector.producer.TransactionJsonProducer"
```

## Différences avec le Producer Protobuf

| Caractéristique | JSON Producer | Protobuf Producer |
|----------------|---------------|-------------------|
| Format | JSON (String) | Protobuf (Binary) |
| Serializer | StringSerializer | KafkaProtobufSerializer |
| Schema Registry | Non requis | Requis |
| Taille des messages | Plus grande | Plus petite |
| Compatibilité | Facile à déboguer | Meilleure performance |

## Test avec Druid

Pour tester l'ingestion JSON dans Druid, utilisez la spec d'ingestion `kafka-transactions-json-producer-spec.json` :

```bash
cd ../druid-ingestion
./scripts/submit-ingestion.sh kafka-transactions-json-producer-spec.json
```

## Arrêt

Pour arrêter le producer, utilisez `Ctrl+C`. Le producer fermera proprement toutes les connexions.

