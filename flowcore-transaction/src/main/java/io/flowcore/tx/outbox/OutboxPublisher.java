package io.flowcore.tx.outbox;

import io.flowcore.api.dto.OutboxEvent;

/**
 * Strategy interface for publishing outbox events to an external broker.
 * <p>
 * Implementations are provided by infrastructure modules (e.g. Kafka, RabbitMQ)
 * and are called by the {@link OutboxPublisherScheduler}.
 */
public interface OutboxPublisher {

    /**
     * Publishes the given outbox event to the external system.
     *
     * @param event the event to publish
     * @return the result indicating success or failure
     */
    PublishResult publish(OutboxEvent event);
}
