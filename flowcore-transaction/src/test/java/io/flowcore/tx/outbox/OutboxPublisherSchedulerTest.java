package io.flowcore.tx.outbox;

import io.flowcore.api.OutboxService;
import io.flowcore.api.dto.FailureInfo;
import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.tx.config.TransactionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private OutboxPublisher publisher;

    private TransactionProperties properties;
    private OutboxPublisherScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new TransactionProperties();
        scheduler = new OutboxPublisherScheduler(outboxService, publisher, properties);
    }

    // ---- Helper ----

    private OutboxEvent buildEvent(UUID id, int publishAttempts) {
        return new OutboxEvent(
                id,
                "Order",
                "order-123",
                "OrderCreated",
                "order-123-created",
                "{\"orderId\":\"order-123\"}",
                Map.of(),
                "PENDING",
                publishAttempts,
                Instant.now(),
                Instant.now().minusSeconds(60),
                null
        );
    }

    // ========================================================================
    // pollAndPublish()
    // ========================================================================

    @Nested
    @DisplayName("pollAndPublish")
    class PollAndPublishTests {

        @Test
        @DisplayName("should do nothing when no due events")
        void shouldDoNothingWhenNoEvents() {
            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(Collections.emptyList());

            scheduler.pollAndPublish();

            verify(publisher, never()).publish(any());
            verify(outboxService, never()).markPublished(any(), any());
            verify(outboxService, never()).markFailed(any(), any(), any());
        }

        @Test
        @DisplayName("should mark as PUBLISHED on successful publish")
        void shouldMarkPublishedOnSuccess() {
            UUID eventId = UUID.randomUUID();
            OutboxEvent event = buildEvent(eventId, 0);

            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(List.of(event));
            when(publisher.publish(event)).thenReturn(new PublishResult.Success());

            scheduler.pollAndPublish();

            verify(outboxService).markPublished(eq(eventId), any(Instant.class));
            verify(outboxService, never()).markFailed(any(), any(), any());
        }

        @Test
        @DisplayName("should mark as FAILED when publisher returns Failure result")
        void shouldMarkFailedOnPublishFailureResult() {
            UUID eventId = UUID.randomUUID();
            OutboxEvent event = buildEvent(eventId, 1);

            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(List.of(event));
            when(publisher.publish(event)).thenReturn(
                    new PublishResult.Failure("broker unavailable", Duration.ofSeconds(5)));

            scheduler.pollAndPublish();

            ArgumentCaptor<FailureInfo> infoCaptor = ArgumentCaptor.forClass(FailureInfo.class);
            verify(outboxService).markFailed(eq(eventId), infoCaptor.capture(), any(Instant.class));

            FailureInfo info = infoCaptor.getValue();
            assertThat(info.errorCode()).isEqualTo("PUBLISH_FAILED");
            assertThat(info.errorDetail()).isEqualTo("broker unavailable");
        }

        @Test
        @DisplayName("should mark as FAILED with backoff when publisher throws exception")
        void shouldMarkFailedOnPublisherException() {
            UUID eventId = UUID.randomUUID();
            OutboxEvent event = buildEvent(eventId, 2);

            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(List.of(event));
            when(publisher.publish(event)).thenThrow(new RuntimeException("Connection refused"));

            scheduler.pollAndPublish();

            ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(outboxService).markFailed(eq(eventId), any(FailureInfo.class), nextAttemptCaptor.capture());

            // Next attempt must be in the future
            assertThat(nextAttemptCaptor.getValue()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("should process multiple events in batch")
        void shouldProcessMultipleEvents() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            OutboxEvent event1 = buildEvent(id1, 0);
            OutboxEvent event2 = buildEvent(id2, 0);

            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(List.of(event1, event2));
            when(publisher.publish(event1)).thenReturn(new PublishResult.Success());
            when(publisher.publish(event2)).thenReturn(new PublishResult.Success());

            scheduler.pollAndPublish();

            verify(outboxService).markPublished(eq(id1), any(Instant.class));
            verify(outboxService).markPublished(eq(id2), any(Instant.class));
        }

        @Test
        @DisplayName("should apply exponential backoff on exception")
        void shouldApplyExponentialBackoff() {
            UUID eventId = UUID.randomUUID();
            // attemptsSoFar=3, baseDelay=1000, expected delay = min(1000 * 8, 60000) = 8000ms
            OutboxEvent event = buildEvent(eventId, 3);

            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(List.of(event));
            when(publisher.publish(event)).thenThrow(new RuntimeException("fail"));

            scheduler.pollAndPublish();

            ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(outboxService).markFailed(eq(eventId), any(FailureInfo.class), nextAttemptCaptor.capture());

            Instant nextAttempt = nextAttemptCaptor.getValue();
            Duration backoff = Duration.between(Instant.now(), nextAttempt);
            // Allow some slack for test execution time
            assertThat(backoff.toMillis()).isBetween(7000L, 10000L);
        }

        @Test
        @DisplayName("should cap backoff at maxRetryDelayMs")
        void shouldCapBackoffAtMaxDelay() {
            UUID eventId = UUID.randomUUID();
            // attemptsSoFar=20, baseDelay=1000, raw = 1000 * 2^20 >> maxDelay=60000
            OutboxEvent event = buildEvent(eventId, 20);

            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(List.of(event));
            when(publisher.publish(event)).thenThrow(new RuntimeException("fail"));

            scheduler.pollAndPublish();

            ArgumentCaptor<Instant> nextAttemptCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(outboxService).markFailed(eq(eventId), any(FailureInfo.class), nextAttemptCaptor.capture());

            Instant nextAttempt = nextAttemptCaptor.getValue();
            Duration backoff = Duration.between(Instant.now(), nextAttempt);
            assertThat(backoff.toMillis()).isLessThanOrEqualTo(60000L);
        }

        @Test
        @DisplayName("should use batchSize from properties when fetching batch")
        void shouldUseBatchSizeFromProperties() {
            when(outboxService.fetchDueBatch(properties.outbox().batchSize()))
                    .thenReturn(Collections.emptyList());

            scheduler.pollAndPublish();

            verify(outboxService).fetchDueBatch(properties.outbox().batchSize());
        }
    }
}
