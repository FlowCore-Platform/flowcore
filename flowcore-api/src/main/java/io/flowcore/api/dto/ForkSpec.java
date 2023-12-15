package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * A single fork specification (used inside WorkflowDefinition.forkJoinSpecs).
 */
public record ForkSpec(
    @NotBlank String forkStepId,
    List<String> branchNames,
    @NotBlank String joinStepId,
    @NotBlank String joinPolicy
) {}
