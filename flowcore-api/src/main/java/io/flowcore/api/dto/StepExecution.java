package io.flowcore.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single step execution attempt within a workflow instance.
 *
 * @param id                   unique execution identifier
 * @param workflowInstanceId   the parent workflow instance
 * @param tokenId              the token that triggered this execution
 * @param stepId               the step definition identifier
 * @param attempt              retry attempt number (1-based)
 * @param status               execution status (PENDING, RUNNING, COMPLETED, FAILED, TIMED_OUT)
 * @param startedAt            timestamp when execution started
 * @param finishedAt           timestamp when execution finished (null if still running)
 * @param errorCode            machine-readable error code on failure (may be null)
 * @param errorDetail          human-readable error detail on failure (may be null)
 * @param resultJson           serialized step result (JSON, may be null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepExecution(
        @NotNull UUID id,
        @NotNull UUID workflowInstanceId,
        @NotNull UUID tokenId,
        @NotBlank String stepId,
        int attempt,
        @NotBlank String status,
        @NotNull Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorDetail,
        String resultJson
) {}
