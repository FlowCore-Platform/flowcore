package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Command to send a signal (external event) to a running workflow instance.
 *
 * @param instanceId     the target workflow instance identifier
 * @param eventType      the type of signal event
 * @param payload        signal payload data
 * @param idempotencyKey optional key for idempotent signal delivery
 */
public record SignalWorkflowCommand(
        @NotNull UUID instanceId,
        @NotBlank String eventType,
        Map<String, Object> payload,
        String idempotencyKey
) {}
