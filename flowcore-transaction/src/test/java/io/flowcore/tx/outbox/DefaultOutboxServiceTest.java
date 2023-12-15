package io.flowcore.tx.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.dto.FailureInfo;
import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.api.dto.OutboxEventDraft;
import io.flowcore.tx.config.TransactionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultOutboxServiceTest {

    @Mock
    private OutboxEventRepository repository;

    private TransactionProperties properties;
    private ObjectMapper objectMapper;
    private DefaultOutboxService service;

    @BeforeEach
    void setUp() {
        properties = new TransactionProperties();
        objectMapper = new ObjectMapper();
        service = new DefaultOutboxService(repository, properties, objectMapper);
    }

    @Nested
    @DisplayName("enqueue")
    class EnqueueTests {

        @Test
        @DisplayName("should persist entity with PENDING status and return UUID")
        void shouldPersistWithPendingStatus() {
            OutboxEventDraft draft = new OutboxEventDraft(
                    "Order", "order-123", "OrderCreated", "order-123-created",
                    "{\"orderId\":\"order-123\"}", Map.of("traceId", "abc")
            );

            ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
            when(repository.saveAndFlush(captor.capture())).thenReturn(null);

            UUID result = service.enqueue(draft);

            assertThat(result).isNotNull();

            OutboxEventEntity saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(result);
            assertThat(saved.getAggregateType()).isEqualTo("Order");
            assertThat(saved.getAggregateId()).isEqualTo("order-123");
            assertThat(saved.getEventType()).isEqualTo("OrderCreated");
            assertThat(saved.getEventKey()).isEqualTo("order-123-created");
            assertThat(saved.getPayloadJson()).isEqualTo("{\"orderId\":\"order-123\"}");
            assertThat(saved.getStatus()).isEqualTo("PENDING");
            assertThat(saved.getPublishAttempts()).isEqualTo(0);
            assertThat(saved.getNextAttemptAt()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getPublishedAt()).isNull();
        }

        @Test
        @DisplayName("should serialize headers as JSON")
        void shouldSerializeHeaders() {
            OutboxEventDraft draft = new OutboxEventDraft(
                    "Order", "order-456", "OrderUpdated", "order-456-updated",
                    null, Map.of("source", "api", "correlationId", "xyz")
            );

            ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
            when(repository.saveAndFlush(captor.capture())).thenReturn(null);

            service.enqueue(draft);

            String headersJson = captor.getValue().getHeadersJson();
            assertThat(headersJson).isNotBlank();
            assertThat(headersJson).contains("source", "api", "correlationId", "xyz");
        }

        @Test
        @DisplayName("should handle null headers gracefully")
        void shouldHandleNullHeaders() {
            OutboxEventDraft draft = new OutboxEventDraft(
                    "Order", "order-789", "OrderDeleted", "order-789-deleted",
                    null, null
            );

            ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
            when(repository.saveAndFlush(captor.capture())).thenReturn(null);

            service.enqueue(draft);

            assertThat(captor.getValue().getHeadersJson()).isNull();
        }
    }

    @Nested
    @DisplayName("fetchDueBatch")
    class FetchDueBatchTests {

        @Test
        @DisplayName("should return empty list when no due events")
        void shouldReturnEmptyWhenNoDueEvents() {
            when(repository.findDueEvents(any(Instant.class), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            List<OutboxEvent> result = service.fetchDueBatch(10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should convert entities to DTOs")
        void shouldConvertEntitiesToDtos() {
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();
            Instant createdAt = now.minusSeconds(60);

            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .id(eventId)
                    .aggregateType("Payment")
                    .aggregateId("pay-001")
                    .eventType("PaymentCompleted")
                    .eventKey("pay-001-completed")
                    .payloadJson("{\"amount\":100}")
                    .headersJson("{\"traceId\":\"t1\"}")
                    .status("PENDING")
                    .publishAttempts(0)
                    .nextAttemptAt(now)
                    .createdAt(createdAt)
                    .build();

            when(repository.findDueEvents(any(Instant.class), any(Pageable.class)))
                    .thenReturn(List.of(entity));

            List<OutboxEvent> result = service.fetchDueBatch(10);

            assertThat(result).hasSize(1);
            OutboxEvent event = result.getFirst();
            assertThat(event.id()).isEqualTo(eventId);
            assertThat(event.aggregateType()).isEqualTo("Payment");
            assertThat(event.aggregateId()).isEqualTo("pay-001");
            assertThat(event.eventType()).isEqualTo("PaymentCompleted");
            assertThat(event.eventKey()).isEqualTo("pay-001-completed");
            assertThat(event.payloadJson()).isEqualTo("{\"amount\":100}");
            assertThat(event.headers()).containsEntry("traceId", "t1");
            assertThat(event.status()).isEqualTo("PENDING");
            assertThat(event.publishAttempts()).isEqualTo(0);
            assertThat(event.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("should pass batch size as page limit")
        void shouldPassBatchSize() {
            when(repository.findDueEvents(any(Instant.class), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            service.fetchDueBatch(25);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(repository).findDueEvents(any(Instant.class), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublishedTests {

        @Test
        @DisplayName("should set status to PUBLISHED and set publishedAt")
        void shouldSetPublishedStatus() {
            UUID eventId = UUID.randomUUID();
            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .id(eventId)
                    .status("PENDING")
                    .build();

            when(repository.findById(eventId)).thenReturn(Optional.of(entity));

            Instant publishedAt = Instant.now();
            service.markPublished(eventId, publishedAt);

            assertThat(entity.getStatus()).isEqualTo("PUBLISHED");
            assertThat(entity.getPublishedAt()).isEqualTo(publishedAt);
        }

        @Test
        @DisplayName("should handle missing entity gracefully")
        void shouldHandleMissingEntity() {
            UUID missingId = UUID.randomUUID();
            when(repository.findById(missingId)).thenReturn(Optional.empty());

            // Should not throw
            service.markPublished(missingId, Instant.now());
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailedTests {

        @Test
        @DisplayName("should increment attempts and keep PENDING when under max")
        void shouldIncrementAndKeepPending() {
            UUID eventId = UUID.randomUUID();
            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .id(eventId)
                    .status("PENDING")
                    .publishAttempts(2)
                    .build();

            when(repository.findById(eventId)).thenReturn(Optional.of(entity));

            FailureInfo info = new FailureInfo("TIMEOUT", "Connection timed out");
            Instant nextAttempt = Instant.now().plusSeconds(30);
            service.markFailed(eventId, info, nextAttempt);

            assertThat(entity.getPublishAttempts()).isEqualTo(3);
            assertThat(entity.getStatus()).isEqualTo("PENDING");
            assertThat(entity.getNextAttemptAt()).isEqualTo(nextAttempt);
        }

        @Test
        @DisplayName("should set DEAD status when max attempts reached")
        void shouldSetDeadOnMaxAttempts() {
            UUID eventId = UUID.randomUUID();
            // maxAttempts defaults to 10 in TransactionProperties.Outbox
            OutboxEventEntity entity = OutboxEventEntity.builder()
                    .id(eventId)
                    .status("PENDING")
                    .publishAttempts(9) // next attempt will be 10 = maxAttempts
                    .build();

            when(repository.findById(eventId)).thenReturn(Optional.of(entity));

            FailureInfo info = new FailureInfo("PERMANENT", "Unrecoverable error");
            service.markFailed(eventId, info, Instant.now().plusSeconds(60));

            assertThat(entity.getPublishAttempts()).isEqualTo(10);
            assertThat(entity.getStatus()).isEqualTo("DEAD");
        }

        @Test
        @DisplayName("should handle missing entity gracefully")
        void shouldHandleMissingEntity() {
            UUID missingId = UUID.randomUUID();
            when(repository.findById(missingId)).thenReturn(Optional.empty());

            // Should not throw
            service.markFailed(missingId, new FailureInfo("ERR", "test"), Instant.now());
        }
    }
}
