package io.flowcore.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Flowcore Kafka module.
 * <p>
 * All properties are prefixed with {@code flowcore.kafka}.
 */
@ConfigurationProperties(prefix = "flowcore.kafka")
public record KafkaProperties(
    String bootstrapServers,
    String topicPrefix,
    boolean eosEnabled,
    String transactionalIdPrefix,
    String consumerGroup,
    boolean autoCreateTopics,
    Producer producer,
    Consumer consumer
) {

    public KafkaProperties() {
        this("localhost:9092", "flowcore.", false, "flowcore-tx-",
                "flowcore-consumer", true, new Producer(), new Consumer());
    }

    /**
     * Producer-specific configuration.
     */
    public record Producer(
        String acks,
        int retries,
        int batchSize,
        int lingerMs
    ) {
        public Producer() {
            this("all", 3, 0, 0);
        }
    }

    /**
     * Consumer-specific configuration.
     */
    public record Consumer(
        String autoOffsetReset,
        int maxPollRecords
    ) {
        public Consumer() {
            this("earliest", 100);
        }
    }
}
