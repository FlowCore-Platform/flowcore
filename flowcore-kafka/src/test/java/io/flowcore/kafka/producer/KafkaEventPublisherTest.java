package io.flowcore.kafka.producer;

import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.kafka.config.KafkaProperties;
import io.flowcore.tx.outbox.PublishResult;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor;

    private KafkaEventPublisher publisher;

    private final KafkaProperties properties = new KafkaProperties(
            "localhost:9092", "flowcore.", false, "flowcore-tx-",
            "flowcore-consumer", true,
            new KafkaProperties.Producer(), new KafkaProperties.Consumer());

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(kafkaTemplate, properties);
    }

    private OutboxEvent buildTestEvent() {
        return new OutboxEvent(
                UUID.randomUUID(),
                "Order",
                "order-123",
                "Created",
                "order-123",
                "{\"orderId\":\"order-123\",\"amount\":100.0}",
                Map.of("traceId", "abc-123"),
                "PENDING",
                0,
                Instant.now(),
                Instant.now(),
                null
        );
    }

    private CompletableFuture<SendResult<String, byte[]>> successfulFuture(String topic) {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, byte[]> sendResult = new SendResult<>(null, metadata);
        return CompletableFuture.completedFuture(sendResult);
    }

    @Test
    @DisplayName("publish returns Success when Kafka send completes")
    void publishSuccess() {
        OutboxEvent event = buildTestEvent();
        CompletableFuture<SendResult<String, byte[]>> future =
                successfulFuture("flowcore.Order.Created");

        doReturn(future).when(kafkaTemplate).send(any(ProducerRecord.class));

        PublishResult result = publisher.publish(event);

        assertInstanceOf(PublishResult.Success.class, result);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, byte[]> record = recordCaptor.getValue();
        assertEquals("flowcore.Order.Created", record.topic());
        assertEquals("order-123", record.key());
        assertNotNull(record.value());
    }

    @Test
    @DisplayName("publish returns Failure when Kafka send throws")
    void publishFailure() {
        OutboxEvent event = buildTestEvent();

        CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Broker unavailable"));

        doReturn(future).when(kafkaTemplate).send(any(ProducerRecord.class));

        PublishResult result = publisher.publish(event);

        assertInstanceOf(PublishResult.Failure.class, result);
        PublishResult.Failure failure = (PublishResult.Failure) result;
        assertNotNull(failure.errorMessage());
        assertNotNull(failure.retryAfter());
    }

    @Test
    @DisplayName("resolveTopic constructs correct topic name")
    void resolveTopic() {
        OutboxEvent event = buildTestEvent();
        String topic = publisher.resolveTopic(event);
        assertEquals("flowcore.Order.Created", topic);
    }

    @Test
    @DisplayName("publish handles null payload gracefully")
    void publishNullPayload() {
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(), "Payment", "pay-1", "Processed",
                "pay-1", null, Map.of(), "PENDING", 0,
                Instant.now(), Instant.now(), null);

        CompletableFuture<SendResult<String, byte[]>> future =
                successfulFuture("flowcore.Payment.Processed");

        doReturn(future).when(kafkaTemplate).send(any(ProducerRecord.class));

        PublishResult result = publisher.publish(event);
        assertInstanceOf(PublishResult.Success.class, result);

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, byte[]> record = recordCaptor.getValue();
        assertEquals(0, record.value().length);
    }

    @Test
    @DisplayName("publish propagates headers from event")
    void publishPropagatesHeaders() {
        OutboxEvent event = buildTestEvent();

        CompletableFuture<SendResult<String, byte[]>> future =
                successfulFuture("flowcore.Order.Created");

        doReturn(future).when(kafkaTemplate).send(any(ProducerRecord.class));

        publisher.publish(event);

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, byte[]> record = recordCaptor.getValue();

        var traceIdHeader = record.headers().lastHeader("traceId");
        assertNotNull(traceIdHeader);
        assertEquals("abc-123", new String(traceIdHeader.value()));

        var eventIdHeader = record.headers().lastHeader("x-event-id");
        assertNotNull(eventIdHeader);
        assertEquals(event.id().toString(), new String(eventIdHeader.value()));
    }

    @Test
    @DisplayName("publish uses custom topic prefix")
    void publishCustomTopicPrefix() {
        KafkaProperties customProps = new KafkaProperties(
                "localhost:9092", "myapp.", false, "flowcore-tx-",
                "flowcore-consumer", true,
                new KafkaProperties.Producer(), new KafkaProperties.Consumer());

        KafkaEventPublisher customPublisher = new KafkaEventPublisher(kafkaTemplate, customProps);
        OutboxEvent event = buildTestEvent();

        CompletableFuture<SendResult<String, byte[]>> future =
                successfulFuture("myapp.Order.Created");

        doReturn(future).when(kafkaTemplate).send(any(ProducerRecord.class));

        customPublisher.publish(event);

        verify(kafkaTemplate).send(recordCaptor.capture());
        assertEquals("myapp.Order.Created", recordCaptor.getValue().topic());
    }
}
