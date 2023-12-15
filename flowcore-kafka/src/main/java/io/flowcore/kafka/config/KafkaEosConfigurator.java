package io.flowcore.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configures Exactly-Once Semantics (EOS) for Kafka producers and consumers.
 * <p>
 * When EOS is enabled via {@link KafkaProperties#eosEnabled()}, this component
 * sets the {@code transactional.id} and {@code enable.idempotence} on producer
 * configs and {@code isolation.level=read_committed} on consumer configs.
 */
@Component
public class KafkaEosConfigurator {

    private final KafkaProperties properties;
    private final AtomicInteger transactionalIdCounter = new AtomicInteger(0);

    public KafkaEosConfigurator(KafkaProperties properties) {
        this.properties = properties;
    }

    /**
     * Applies EOS-related configuration to producer properties when EOS is enabled.
     *
     * @param props the producer configuration map to modify
     */
    public void configureProducerProps(Map<String, Object> props) {
        if (!properties.eosEnabled()) {
            return;
        }
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        String txId = properties.transactionalIdPrefix()
                + transactionalIdCounter.getAndIncrement();
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, txId);
    }

    /**
     * Applies EOS-related configuration to consumer properties when EOS is enabled.
     *
     * @param props the consumer configuration map to modify
     */
    public void configureConsumerProps(Map<String, Object> props) {
        if (!properties.eosEnabled()) {
            return;
        }
        props.put("isolation.level", "read_committed");
    }

    /**
     * Applies EOS configuration to a {@link ProducerFactory} by overriding
     * its configuration properties.
     *
     * @param producerFactory the producer factory to configure
     * @param <K>             key type
     * @param <V>             value type
     */
    public <K, V> void configureProducerFactory(ProducerFactory<K, V> producerFactory) {
        Map<String, Object> configs = new HashMap<>(producerFactory.getConfigurationProperties());
        configureProducerProps(configs);
        producerFactory.updateConfigs(configs);
    }

    /**
     * Applies EOS configuration to a {@link ConsumerFactory} by overriding
     * its configuration properties.
     *
     * @param consumerFactory the consumer factory to configure
     * @param <K>             key type
     * @param <V>             value type
     */
    public <K, V> void configureConsumerFactory(ConsumerFactory<K, V> consumerFactory) {
        Map<String, Object> configs = new HashMap<>(consumerFactory.getConfigurationProperties());
        configureConsumerProps(configs);
        consumerFactory.updateConfigs(configs);
    }

    /**
     * Returns whether EOS is enabled.
     *
     * @return true if EOS is enabled
     */
    public boolean isEosEnabled() {
        return properties.eosEnabled();
    }
}
