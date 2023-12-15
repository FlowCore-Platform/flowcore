package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Summary of a step execution for query results.
 */
public record StepExecutionSummary(
    @NotBlank String stepId,
    int attempt,
    @NotBlank String status,
    @NotNull Instant startedAt,
    Instant finishedAt
) {}
