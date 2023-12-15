package io.flowcore.api;

import io.flowcore.api.dto.FailureInfo;
import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.api.dto.OutboxEventDraft;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional outbox pattern service for reliable event publishing.
 */
public interface OutboxService {

    /**
     * Enqueues a new outbox event draft for eventual publishing.
     *
     * @param draft the event draft to persist
     * @return the unique identifier assigned to the persisted event
     */
    UUID enqueue(OutboxEventDraft draft);

    /**
     * Fetches a batch of due (ready-to-publish) outbox events.
     *
     * @param batchSize maximum number of events to return
     * @return list of outbox events ready for publishing
     */
    List<OutboxEvent> fetchDueBatch(int batchSize);

    /**
     * Marks an outbox event as successfully published.
     *
     * @param id          the event identifier
     * @param publishedAt the timestamp when the event was published
     */
    void markPublished(UUID id, Instant publishedAt);

    /**
     * Marks an outbox event as failed and schedules a retry.
     *
     * @param id            the event identifier
     * @param info          failure details
     * @param nextAttemptAt the time at which the next retry should occur
     */
    void markFailed(UUID id, FailureInfo info, Instant nextAttemptAt);
}
