package io.flowcore.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable definition of a workflow type.
 *
 * @param workflowType   unique identifier for this workflow definition (e.g. "payment.v1")
 * @param version        semantic version of the definition
 * @param states         all declared state names
 * @param initialState   the state a new instance starts in
 * @param terminalStates states that mark the workflow as finished
 * @param steps          step definitions keyed by step id
 * @param transitions    transitions connecting states
 * @param forkJoinSpecs  parallel fork/join specifications (may be empty)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDefinition(
        @NotBlank String workflowType,
        @NotBlank String version,
        @NotNull Set<String> states,
        @NotBlank String initialState,
        @NotNull Set<String> terminalStates,
        @NotNull List<StepDef> steps,
        @NotNull List<TransitionDef> transitions,
        List<ForkSpec> forkJoinSpecs
) {}
