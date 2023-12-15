package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command to complete a step execution within a workflow instance.
 *
 * @param instanceId  the workflow instance identifier
 * @param tokenId     the token that triggered this step
 * @param stepId      the step definition identifier
 * @param resultJson  serialized step result (JSON), may be null
 * @param errorCode   machine-readable error code on failure, may be null
 * @param errorDetail human-readable error detail on failure, may be null
 */
public record CompleteStepCommand(
        @NotNull UUID instanceId,
        @NotNull UUID tokenId,
        @NotBlank String stepId,
        String resultJson,
        String errorCode,
        String errorDetail
) {}
