package io.flowcore.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaEosConfiguratorTest {

    private KafkaEosConfigurator eosEnabledConfigurator;
    private KafkaEosConfigurator eosDisabledConfigurator;

    @BeforeEach
    void setUp() {
        KafkaProperties enabledProps = new KafkaProperties(
                "localhost:9092", "flowcore.", true, "flowcore-tx-",
                "flowcore-consumer", true,
                new KafkaProperties.Producer(), new KafkaProperties.Consumer());

        KafkaProperties disabledProps = new KafkaProperties(
                "localhost:9092", "flowcore.", false, "flowcore-tx-",
                "flowcore-consumer", true,
                new KafkaProperties.Producer(), new KafkaProperties.Consumer());

        eosEnabledConfigurator = new KafkaEosConfigurator(enabledProps);
        eosDisabledConfigurator = new KafkaEosConfigurator(disabledProps);
    }

    @Test
    @DisplayName("EOS enabled configures transactional.id and idempotence on producer")
    void eosEnabledProducerConfig() {
        Map<String, Object> props = new HashMap<>();
        eosEnabledConfigurator.configureProducerProps(props);

        assertEquals(true, props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        String txId = (String) props.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        assertNotNull(txId);
        assertTrue(txId.startsWith("flowcore-tx-"));
    }

    @Test
    @DisplayName("EOS enabled configures read_committed isolation on consumer")
    void eosEnabledConsumerConfig() {
        Map<String, Object> props = new HashMap<>();
        eosEnabledConfigurator.configureConsumerProps(props);

        assertEquals("read_committed", props.get("isolation.level"));
    }

    @Test
    @DisplayName("EOS disabled does not modify producer properties")
    void eosDisabledProducerConfig() {
        Map<String, Object> props = new HashMap<>();
        eosDisabledConfigurator.configureProducerProps(props);

        assertNull(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertNull(props.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG));
    }

    @Test
    @DisplayName("EOS disabled does not modify consumer properties")
    void eosDisabledConsumerConfig() {
        Map<String, Object> props = new HashMap<>();
        eosDisabledConfigurator.configureConsumerProps(props);

        assertNull(props.get("isolation.level"));
    }

    @Test
    @DisplayName("Transactional ID counter increments across calls")
    void transactionalIdIncrements() {
        Map<String, Object> props1 = new HashMap<>();
        Map<String, Object> props2 = new HashMap<>();

        eosEnabledConfigurator.configureProducerProps(props1);
        eosEnabledConfigurator.configureProducerProps(props2);

        String txId1 = (String) props1.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        String txId2 = (String) props2.get(ProducerConfig.TRANSACTIONAL_ID_CONFIG);

        assertNotNull(txId1);
        assertNotNull(txId2);
        assertFalse(txId1.equals(txId2),
                "Each producer factory should get a unique transactional ID");
    }

    @Test
    @DisplayName("isEosEnabled returns correct state")
    void isEosEnabled() {
        assertTrue(eosEnabledConfigurator.isEosEnabled());
        assertFalse(eosDisabledConfigurator.isEosEnabled());
    }
}
