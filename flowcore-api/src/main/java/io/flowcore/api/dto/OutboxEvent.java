package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A persisted outbox event awaiting publication.
 */
public record OutboxEvent(
    @NotNull UUID id,
    @NotBlank String aggregateType,
    @NotBlank String aggregateId,
    @NotBlank String eventType,
    @NotBlank String eventKey,
    String payloadJson,
    Map<String, String> headers,
    @NotBlank String status,
    int publishAttempts,
    @NotNull Instant nextAttemptAt,
    @NotNull Instant createdAt,
    Instant publishedAt
) {
    public OutboxEvent {
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
}
