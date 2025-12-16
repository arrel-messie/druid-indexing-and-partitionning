# Kafka Transaction Producer

Generic Kafka Producer for sending transaction events with Protobuf schema to the `Transactions` topic.

## Features

- **Protobuf Schema Support**: Uses Protobuf with Schema Registry
- **Configurable**: Highly configurable via `application.properties`
- **Scheduled Sending**: Sends messages at configurable intervals (default: 30 seconds)
- **Random Data Generation**: Generates realistic transaction data
- **Reliable**: Configurable reliability settings (acks, retries, idempotence)
- **Performance Optimized**: Batch processing, compression, and tuning options

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Kafka cluster running
- Schema Registry running
- Topic `Transactions` created in Kafka

## Building the Project

```bash
mvn clean package
```

This will:
1. Compile the Protobuf schema
2. Generate Java classes from the `.proto` file
3. Compile the Java code
4. Create an executable JAR file

## Configuration

Edit `src/main/resources/application.properties` to configure:

### Kafka Configuration
```properties
kafka.bootstrap.servers=localhost:9092
kafka.topic.name=Transactions
kafka.client.id=transaction-producer
```

### Schema Registry Configuration
```properties
schema.registry.url=http://localhost:8085
schema.registry.subject.name=Transactions-value
```

### Producer Behavior
```properties
# Send interval in seconds
producer.send.interval.seconds=30

# Number of messages to send (-1 for infinite)
producer.send.count=-1

# Enable random data generation
producer.random.data.enabled=true
```

### Data Generation
```properties
data.montant.min=10.0
data.montant.max=1000.0
data.commande.id.prefix=CMD-
```

### Producer Reliability & Performance
```properties
producer.acks=all
producer.retries=3
producer.batch.size=16384
producer.linger.ms=10
producer.compression.type=snappy
producer.enable.idempotence=true
```

## Running the Producer

### Using Maven
```bash
mvn exec:java -Dexec.mainClass="com.kafka.injector.producer.TransactionProducer"
```

### Using the JAR file
```bash
java -jar target/kafka-producer-1.0.0.jar
```

### With custom configuration
```bash
java -Dconfig.file=/path/to/custom.properties -jar target/kafka-producer-1.0.0.jar
```

## Example Output

```
2025-12-08 18:30:00.123 [main] INFO  TransactionProducer - TransactionProducer initialized
2025-12-08 18:30:00.125 [main] INFO  TransactionProducer - Starting TransactionProducer...
2025-12-08 18:30:00.126 [main] INFO  TransactionProducer - TransactionProducer started. Sending messages every 30 seconds
2025-12-08 18:30:00.250 [kafka-producer-network-thread] INFO  TransactionProducer - Message #1 sent successfully - Topic: Transactions, Partition: 0, Offset: 0, Key: 550e8400-e29b-41d4-a716-446655440000
2025-12-08 18:30:30.350 [kafka-producer-network-thread] INFO  TransactionProducer - Message #2 sent successfully - Topic: Transactions, Partition: 0, Offset: 1, Key: 6ba7b810-9dad-11d1-80b4-00c04fd430c8
```

## Message Structure

Each message contains:
- **transaction_id**: UUID string
- **montant**: Random amount between min and max
- **methode**: Random payment method (CARTE, VIREMENT, PAYPAL, CRYPTO)
- **commande_id**: Generated order ID with prefix
- **timestamp**: Current timestamp in milliseconds

## Schema Registry

The producer automatically registers the Protobuf schema with Schema Registry if it doesn't exist. The schema subject name follows the pattern: `{topic-name}-value`.

## Stopping the Producer

Press `Ctrl+C` to gracefully stop the producer. The shutdown hook will:
1. Stop the scheduler
2. Flush pending messages
3. Close the producer connection

## Troubleshooting

### Schema Registry Connection Issues
- Verify Schema Registry is running: `curl http://localhost:8085/subjects`
- Check the `schema.registry.url` in configuration

### Kafka Connection Issues
- Verify Kafka is running: `docker ps | grep kafka`
- Check the `kafka.bootstrap.servers` in configuration
- Ensure the topic exists: `kafka-topics --list --bootstrap-server localhost:9092`

### Protobuf Compilation Issues
- Ensure `protoc` is installed or use the Maven plugin (included)
- Check the `.proto` file syntax

## Advanced Configuration

### Environment Variables
You can override configuration using environment variables:
```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export SCHEMA_REGISTRY_URL=http://localhost:8085
export PRODUCER_SEND_INTERVAL_SECONDS=60
```

### Custom Data Generation
Modify the `createTransaction()` method in `TransactionProducer.java` to customize data generation logic.

## License

This project is provided as-is for demonstration purposes.



