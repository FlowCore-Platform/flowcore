package io.flowcore.kafka.config;

import io.flowcore.api.InboxService;
import io.flowcore.api.WorkflowEngine;
import io.flowcore.kafka.consumer.KafkaCommandConsumer;
import io.flowcore.kafka.producer.KafkaEventPublisher;
import io.flowcore.tx.outbox.OutboxPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for the Flowcore Kafka module.
 * <p>
 * Registers beans for Kafka properties, EOS configurator, event publisher,
 * and command consumer. All beans are conditional on missing existing
 * definitions to allow user overrides.
 */
@AutoConfiguration
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaEosConfigurator kafkaEosConfigurator(KafkaProperties properties) {
        return new KafkaEosConfigurator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, byte[]> kafkaProducerFactory(
            KafkaProperties properties,
            KafkaEosConfigurator eosConfigurator) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, properties.producer().acks());
        props.put(ProducerConfig.RETRIES_CONFIG, properties.producer().retries());

        if (properties.producer().batchSize() > 0) {
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, properties.producer().batchSize());
        }
        if (properties.producer().lingerMs() > 0) {
            props.put(ProducerConfig.LINGER_MS_CONFIG, properties.producer().lingerMs());
        }

        eosConfigurator.configureProducerProps(props);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, byte[]> kafkaTemplate(
            ProducerFactory<String, byte[]> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaEventPublisher kafkaEventPublisher(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            KafkaProperties properties) {
        return new KafkaEventPublisher(kafkaTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(ConsumerFactory.class)
    public ConsumerFactory<String, byte[]> kafkaConsumerFactory(
            KafkaProperties properties,
            KafkaEosConfigurator eosConfigurator) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.consumer().autoOffsetReset());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.consumer().maxPollRecords());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, properties.consumerGroup());

        eosConfigurator.configureConsumerProps(props);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConcurrentMessageListenerContainer<String, byte[]> kafkaCommandListenerContainer(
            ConsumerFactory<String, byte[]> consumerFactory) {
        ContainerProperties containerProperties =
                new ContainerProperties("flowcore.commands.default");
        return new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaCommandConsumer kafkaCommandConsumer(
            InboxService inboxService,
            WorkflowEngine workflowEngine,
            org.springframework.kafka.core.KafkaTemplate<String, byte[]> kafkaTemplate,
            KafkaProperties properties,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new KafkaCommandConsumer(inboxService, workflowEngine, objectMapper);
    }
}
