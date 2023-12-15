package io.flowcore.api.dto;

import java.util.Map;

/**
 * Attributes of the target object for ABAC evaluation.
 */
public record ObjectAttributes(
    String workflowType,
    String businessKey,
    String amount,
    String currency,
    String country,
    String provider,
    Map<String, String> additional
) {
    public ObjectAttributes {
        additional = additional != null ? Map.copyOf(additional) : Map.of();
    }
}
