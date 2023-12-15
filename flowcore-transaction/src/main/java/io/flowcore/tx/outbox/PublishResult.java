package io.flowcore.tx.outbox;

import java.time.Duration;

/**
 * Result of an attempt to publish an outbox event.
 */
public sealed interface PublishResult {

    /**
     * The event was successfully published.
     */
    record Success() implements PublishResult {}

    /**
     * The event could not be published.
     *
     * @param errorMessage human-readable error description
     * @param retryAfter   suggested delay before the next retry attempt
     */
    record Failure(String errorMessage, Duration retryAfter) implements PublishResult {}
}
