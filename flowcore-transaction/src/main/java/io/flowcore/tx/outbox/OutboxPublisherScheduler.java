package io.flowcore.tx.outbox;

import io.flowcore.api.dto.FailureInfo;
import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.tx.config.TransactionProperties;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled component that polls pending outbox events and delegates
 * their publishing to the configured {@link OutboxPublisher}.
 * <p>
 * On successful publishing the event is marked as PUBLISHED; on failure
 * an exponential backoff is applied and the event remains PENDING (or
 * transitions to DEAD when the maximum number of attempts is exceeded).
 */
@Service
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final io.flowcore.api.OutboxService outboxService;
    private final OutboxPublisher publisher;
    private final TransactionProperties properties;

    public OutboxPublisherScheduler(io.flowcore.api.OutboxService outboxService,
                                    OutboxPublisher publisher,
                                    TransactionProperties properties) {
        this.outboxService = outboxService;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${flowcore.tx.outbox.poll-interval-ms:500}")
    @Transactional
    public void pollAndPublish() {
        int batchSize = properties.outbox().batchSize();
        List<OutboxEvent> batch = outboxService.fetchDueBatch(batchSize);

        for (OutboxEvent event : batch) {
            try {
                PublishResult result = publisher.publish(event);
                if (result instanceof PublishResult.Success) {
                    outboxService.markPublished(event.id(), Instant.now());
                } else if (result instanceof PublishResult.Failure failure) {
                    handleFailure(event, failure.errorMessage(), failure.retryAfter());
                }
            } catch (Exception ex) {
                log.error("Unexpected error publishing outbox event id={}", event.id(), ex);
                handleFailure(event, ex.getMessage(), calculateBackoff(event.publishAttempts()));
            }
        }
    }

    private void handleFailure(OutboxEvent event, String errorMessage, Duration retryAfter) {
        Instant nextAttempt = Instant.now().plus(retryAfter);
        FailureInfo info = new FailureInfo("PUBLISH_FAILED", errorMessage);
        outboxService.markFailed(event.id(), info, nextAttempt);
    }

    private Duration calculateBackoff(int attemptsSoFar) {
        long baseDelay = properties.outbox().baseRetryDelayMs();
        long maxDelay = properties.outbox().maxRetryDelayMs();
        long delay = Math.min(baseDelay * (1L << attemptsSoFar), maxDelay);
        return Duration.ofMillis(delay);
    }
}
