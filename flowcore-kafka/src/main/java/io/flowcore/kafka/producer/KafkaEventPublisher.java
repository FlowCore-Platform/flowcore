package io.flowcore.kafka.producer;

import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.kafka.config.KafkaProperties;
import io.flowcore.tx.outbox.OutboxPublisher;
import io.flowcore.tx.outbox.PublishResult;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka-based implementation of {@link OutboxPublisher}.
 * <p>
 * Publishes {@link OutboxEvent} instances to Kafka topics using the convention:
 * {@code <topicPrefix><aggregateType>.<eventType>}.
 * The message key is derived from the event's {@code eventKey} field and
 * headers are propagated from the outbox event metadata.
 */
@Service
public class KafkaEventPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final KafkaProperties properties;

    public KafkaEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate,
                               KafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public PublishResult publish(OutboxEvent event) {
        String topic = resolveTopic(event);
        byte[] payload = serializePayload(event);
        List<Header> headers = buildHeaders(event);

        ProducerRecord<String, byte[]> record =
                new ProducerRecord<>(topic, null, event.eventKey(), payload, headers);

        try {
            kafkaTemplate.send(record).get();
            log.debug("Published event {} to topic {}", event.id(), topic);
            return new PublishResult.Success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing event {} to topic {}", event.id(), topic, e);
            return new PublishResult.Failure(
                    "Publishing interrupted: " + e.getMessage(),
                    Duration.ofSeconds(5));
        } catch (Exception e) {
            log.error("Failed to publish event {} to topic {}", event.id(), topic, e);
            return new PublishResult.Failure(
                    "Publishing failed: " + e.getMessage(),
                    Duration.ofSeconds(5));
        }
    }

    /**
     * Resolves the Kafka topic name for the given event.
     * Format: {@code <topicPrefix><aggregateType>.<eventType>}
     */
    String resolveTopic(OutboxEvent event) {
        return properties.topicPrefix() + event.aggregateType() + "." + event.eventType();
    }

    private byte[] serializePayload(OutboxEvent event) {
        String payload = event.payloadJson();
        return payload != null
                ? payload.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
    }

    private List<Header> buildHeaders(OutboxEvent event) {
        List<Header> headers = new ArrayList<>();
        headers.add(new RecordHeader("x-event-id",
                event.id().toString().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-aggregate-type",
                event.aggregateType().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-aggregate-id",
                event.aggregateId().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-event-type",
                event.eventType().getBytes(StandardCharsets.UTF_8)));

        if (event.headers() != null) {
            event.headers().forEach((key, value) ->
                    headers.add(new RecordHeader(key,
                            value.getBytes(StandardCharsets.UTF_8))));
        }
        return headers;
    }
}
