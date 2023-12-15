package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Specification for a fork/join (parallel execution) pattern within a workflow.
 *
 * @param forkStepId the step that initiates the fork
 * @param branchNames logical names for each parallel branch
 * @param joinStepId  the step that waits for all branches to complete
 * @param joinPolicy  the join policy (e.g. "wait_all", "wait_any")
 */
public record ForkJoinSpec(
        @NotBlank String forkStepId,
        List<String> branchNames,
        @NotBlank String joinStepId,
        @NotBlank String joinPolicy
) {}
