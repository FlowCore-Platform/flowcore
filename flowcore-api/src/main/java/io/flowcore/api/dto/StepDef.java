package io.flowcore.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Definition of a single workflow step (state-machine transition with an activity).
 *
 * @param id             unique step identifier within the workflow definition
 * @param kind           step kind (e.g. "task", "event", "decision")
 * @param fromState      source state name
 * @param toState        target state name
 * @param activity       the activity to execute, may be null for no-op steps
 * @param retryPolicy    retry configuration, may be null
 * @param timeoutPolicy  timeout configuration, may be null
 * @param compensation   compensation action, may be null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepDef(
        @NotBlank String id,
        @NotBlank String kind,
        @NotBlank String fromState,
        @NotBlank String toState,
        ActivityDef activity,
        RetryPolicyDef retryPolicy,
        TimeoutPolicyDef timeoutPolicy,
        CompensationDef compensation
) {}
