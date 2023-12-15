package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Command to start a new workflow instance.
 *
 * @param workflowType   the workflow definition type to instantiate
 * @param businessKey    optional application-level correlation key
 * @param contextData    initial context data for the workflow
 * @param idempotencyKey optional key for idempotent start requests
 */
public record StartWorkflowCommand(
        @NotBlank String workflowType,
        String businessKey,
        Map<String, Object> contextData,
        String idempotencyKey
) {}
