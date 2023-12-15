package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Draft event to be enqueued in the transactional outbox.
 */
public record OutboxEventDraft(
    @NotBlank String aggregateType,
    @NotBlank String aggregateId,
    @NotBlank String eventType,
    @NotBlank String eventKey,
    String payloadJson,
    Map<String, String> headers
) {
    public OutboxEventDraft {
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
}
