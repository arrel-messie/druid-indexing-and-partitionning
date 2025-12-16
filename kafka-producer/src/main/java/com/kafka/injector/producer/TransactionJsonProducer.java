package com.kafka.injector.producer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kafka.injector.model.Transaction;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generic Kafka Producer for Transactions with JSON format
 */
public class TransactionJsonProducer {
    private static final Logger logger = LoggerFactory.getLogger(TransactionJsonProducer.class);
    
    private final Configuration config;
    private final Producer<String, String> producer;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private final AtomicLong messageCount;
    private final Gson gson;
    
    private final String topicName;
    private final int sendIntervalSeconds;
    private final long sendCount;
    private final boolean randomDataEnabled;
    private final double montantMin;
    private final double montantMax;
    private final String commandeIdPrefix;
    
    private static final String[] METHODES_PAIEMENT = {"CARTE", "VIREMENT", "PAYPAL", "CRYPTO"};

    public TransactionJsonProducer(Configuration config) {
        this.config = config;
        this.producer = createProducer();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.random = new Random();
        this.messageCount = new AtomicLong(0);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        
        // Load configuration
        this.topicName = config.getString("kafka.topic.name", "TransactionsJSON");
        this.sendIntervalSeconds = config.getInt("producer.send.interval.seconds", 30);
        this.sendCount = config.getLong("producer.send.count", -1);
        this.randomDataEnabled = config.getBoolean("producer.random.data.enabled", true);
        this.montantMin = config.getDouble("data.montant.min", 10.0);
        this.montantMax = config.getDouble("data.montant.max", 1000.0);
        this.commandeIdPrefix = config.getString("data.commande.id.prefix", "CMD-");
        
        logger.info("TransactionJsonProducer initialized with configuration:");
        logger.info("  Topic: {}", topicName);
        logger.info("  Send interval: {} seconds", sendIntervalSeconds);
        logger.info("  Send count: {} (-1 = infinite)", sendCount);
        logger.info("  Random data: {}", randomDataEnabled);
        logger.info("  Format: JSON");
    }

    private Producer<String, String> createProducer() {
        Properties props = new Properties();
        
        // Kafka bootstrap servers
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
            config.getString("kafka.bootstrap.servers", "localhost:9092"));
        props.put(ProducerConfig.CLIENT_ID_CONFIG, 
            config.getString("kafka.client.id", "transaction-json-producer"));
        
        // Serializers - JSON as String
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Producer reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, 
            config.getString("producer.acks", "all"));
        props.put(ProducerConfig.RETRIES_CONFIG, 
            config.getInt("producer.retries", 3));
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, 
            config.getBoolean("producer.enable.idempotence", true));
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 
            config.getInt("producer.max.in.flight.requests.per.connection", 5));
        
        // Performance settings
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 
            config.getInt("producer.batch.size", 16384));
        props.put(ProducerConfig.LINGER_MS_CONFIG, 
            config.getInt("producer.linger.ms", 10));
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 
            config.getLong("producer.buffer.memory", 33554432L));
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, 
            config.getString("producer.compression.type", "snappy"));
        
        return new KafkaProducer<>(props);
    }

    public void start() {
        logger.info("Starting TransactionJsonProducer...");
        
        scheduler.scheduleAtFixedRate(
            this::sendTransaction,
            0,
            sendIntervalSeconds,
            TimeUnit.SECONDS
        );
        
        logger.info("TransactionJsonProducer started. Sending messages every {} seconds", sendIntervalSeconds);
    }

    public void stop() {
        logger.info("Stopping TransactionJsonProducer...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        producer.close();
        logger.info("TransactionJsonProducer stopped");
    }

    private void sendTransaction() {
        // Check if we've reached the send count limit
        if (sendCount > 0 && messageCount.get() >= sendCount) {
            logger.info("Reached send count limit ({}). Stopping producer.", sendCount);
            stop();
            return;
        }
        
        try {
            Transaction transaction = createTransaction();
            String key = transaction.getTransaction_id();
            String jsonValue = gson.toJson(transaction);
            
            ProducerRecord<String, String> record = 
                new ProducerRecord<>(topicName, key, jsonValue);
            
            producer.send(record, (metadata, exception) -> {
                long count = messageCount.incrementAndGet();
                if (exception != null) {
                    logger.error("Error sending message #{}: {}", count, exception.getMessage(), exception);
                } else {
                    logger.info("Message #{} sent successfully - Topic: {}, Partition: {}, Offset: {}, Key: {}", 
                        count, metadata.topic(), metadata.partition(), metadata.offset(), key);
                    logger.debug("JSON payload: {}", jsonValue);
                }
            });
            
        } catch (Exception e) {
            logger.error("Error creating or sending transaction: {}", e.getMessage(), e);
        }
    }

    private Transaction createTransaction() {
        String transactionId = UUID.randomUUID().toString();
        double montant = randomDataEnabled 
            ? montantMin + (montantMax - montantMin) * random.nextDouble()
            : montantMin;
        String methode = randomDataEnabled
            ? METHODES_PAIEMENT[random.nextInt(METHODES_PAIEMENT.length)]
            : METHODES_PAIEMENT[0];
        String commandeId = commandeIdPrefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long timestamp = System.currentTimeMillis();
        
        return new Transaction(transactionId, montant, methode, commandeId, timestamp);
    }

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(TransactionJsonProducer.class);
        
        try {
            // Load configuration - try application-json.properties first, fallback to application.properties
            Configurations configs = new Configurations();
            Configuration config;
            try {
                config = configs.properties("application-json.properties");
            } catch (ConfigurationException e) {
                // Fallback to default if json-specific config doesn't exist
                config = configs.properties("application.properties");
            }
            
            // Create and start producer
            TransactionJsonProducer producer = new TransactionJsonProducer(config);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered");
                producer.stop();
            }));
            
            producer.start();
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (ConfigurationException e) {
            logger.error("Configuration error: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}

