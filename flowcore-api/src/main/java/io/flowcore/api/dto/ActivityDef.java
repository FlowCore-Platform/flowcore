package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Definition of an activity (adapter call) within a workflow step.
 *
 * @param adapter   the provider adapter name (e.g. "stripe", "kafka")
 * @param operation the operation name within the adapter (e.g. "createCharge")
 */
public record ActivityDef(
        @NotBlank String adapter,
        @NotBlank String operation
) {}
