package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Context for a provider adapter call.
 */
public record ProviderCallContext(
    @NotBlank String correlationId,
    @NotBlank String workflowType,
    @NotNull UUID workflowInstanceId,
    String businessKey,
    Map<String, String> headers,
    Duration deadline,
    int attempt
) {
    public ProviderCallContext {
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
}
