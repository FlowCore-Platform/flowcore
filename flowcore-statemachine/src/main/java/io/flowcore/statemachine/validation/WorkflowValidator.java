package io.flowcore.statemachine.validation;

import io.flowcore.api.dto.ForkSpec;
import io.flowcore.api.dto.RetryPolicyDef;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TimeoutPolicyDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.TriggerDef;
import io.flowcore.api.dto.WorkflowDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates a {@link WorkflowDefinition} against structural and semantic rules.
 *
 * <p>Validation rules:</p>
 * <ul>
 *   <li>{@code workflowType} must match {@code ^[a-zA-Z0-9_.-]{3,64}$}</li>
 *   <li>{@code initialState} must be present in {@code states}</li>
 *   <li>All transition {@code from}/{@code to} states must exist in {@code states}</li>
 *   <li>No unreachable states (via graph reachability from initial state)</li>
 *   <li>No dead-end non-terminal states (unless {@code allowDeadEnds} is true)</li>
 *   <li>Step IDs must be unique</li>
 *   <li>Retry policy: {@code maxAttempts >= 1}, {@code baseDelayMs >= 10},
 *       {@code maxDelayMs >= baseDelayMs}</li>
 *   <li>Timeout: {@code timeoutMs >= 100}, if present {@code onTimeoutState} must reference a state</li>
 *   <li>Fork/join: fork must declare branch names; join must reference all branch names</li>
 *   <li>Timer names must be unique per workflow</li>
 * </ul>
 *
 * <p>This class is thread-safe and holds no mutable state.</p>
 */
public class WorkflowValidator {

    private static final Pattern WORKFLOW_TYPE_PATTERN = Pattern.compile("^[a-zA-Z0-9_.\\-]{3,64}$");

    /**
     * Validates the given workflow definition with default settings (dead-ends not allowed).
     *
     * @param definition the workflow definition to validate
     * @throws WorkflowValidationException if validation fails
     */
    public void validate(WorkflowDefinition definition) {
        validate(definition, false);
    }

    /**
     * Validates the given workflow definition.
     *
     * @param definition   the workflow definition to validate
     * @param allowDeadEnds if true, non-terminal dead-end states are allowed
     * @throws WorkflowValidationException if validation fails
     */
    public void validate(WorkflowDefinition definition, boolean allowDeadEnds) {
        validateWorkflowType(definition.workflowType());
        validateInitialState(definition);
        validateTransitionStates(definition);
        validateStepIds(definition);
        validateRetryPolicies(definition);
        validateTimeoutPolicies(definition);
        validateForkJoinSpecs(definition);

        // Build the adjacency graph from steps and transitions
        Map<String, Set<String>> adjacencyMap = buildAdjacencyMap(definition);
        Set<String> reachableStates = computeReachableStates(definition.initialState(), adjacencyMap);

        validateReachability(definition, reachableStates);
        if (!allowDeadEnds) {
            validateNoDeadEnds(definition, adjacencyMap);
        }
        validateTimerUniqueness(definition);
    }

    // -- Workflow type validation ------------------------------------------

    private void validateWorkflowType(String workflowType) {
        if (workflowType == null || !WORKFLOW_TYPE_PATTERN.matcher(workflowType).matches()) {
            throw new WorkflowValidationException(
                    WorkflowValidationException.ErrorCode.INVALID_WORKFLOW_TYPE,
                    "workflowType '" + workflowType + "' does not match pattern ^[a-zA-Z0-9_.-]{3,64}$");
        }
    }

    // -- Initial state validation ------------------------------------------

    private void validateInitialState(WorkflowDefinition definition) {
        if (!definition.states().contains(definition.initialState())) {
            throw new WorkflowValidationException(
                    WorkflowValidationException.ErrorCode.INITIAL_STATE_NOT_IN_STATES,
                    "initialState '" + definition.initialState()
                            + "' is not present in declared states " + definition.states());
        }
    }

    // -- Transition state references validation ----------------------------

    private void validateTransitionStates(WorkflowDefinition definition) {
        Set<String> states = definition.states();
        for (TransitionDef transition : definition.transitions()) {
            if (!states.contains(transition.from())) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.TRANSITION_REFERENCES_UNKNOWN_STATE,
                        "transition '" + transition.id() + "' references unknown 'from' state: "
                                + transition.from());
            }
            if (!states.contains(transition.to())) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.TRANSITION_REFERENCES_UNKNOWN_STATE,
                        "transition '" + transition.id() + "' references unknown 'to' state: "
                                + transition.to());
            }
        }
        // Also validate step from/to states
        for (StepDef step : definition.steps()) {
            if (!states.contains(step.fromState())) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.TRANSITION_REFERENCES_UNKNOWN_STATE,
                        "step '" + step.id() + "' references unknown 'fromState': " + step.fromState());
            }
            if (!states.contains(step.toState())) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.TRANSITION_REFERENCES_UNKNOWN_STATE,
                        "step '" + step.id() + "' references unknown 'toState': " + step.toState());
            }
        }
    }

    // -- Step ID uniqueness validation -------------------------------------

    private void validateStepIds(WorkflowDefinition definition) {
        Set<String> seen = new HashSet<>();
        for (StepDef step : definition.steps()) {
            if (!seen.add(step.id())) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.DUPLICATE_STEP_ID,
                        "duplicate step id: '" + step.id() + "'");
            }
        }
    }

    // -- Retry policy validation -------------------------------------------

    private void validateRetryPolicies(WorkflowDefinition definition) {
        for (StepDef step : definition.steps()) {
            RetryPolicyDef retry = step.retryPolicy();
            if (retry != null) {
                if (retry.maxAttempts() < 1) {
                    throw new WorkflowValidationException(
                            WorkflowValidationException.ErrorCode.INVALID_RETRY_POLICY,
                            "step '" + step.id() + "': maxAttempts must be >= 1, got: "
                                    + retry.maxAttempts());
                }
                if (retry.baseDelayMs() < 10) {
                    throw new WorkflowValidationException(
                            WorkflowValidationException.ErrorCode.INVALID_RETRY_POLICY,
                            "step '" + step.id() + "': baseDelayMs must be >= 10, got: "
                                    + retry.baseDelayMs());
                }
                if (retry.maxDelayMs() < retry.baseDelayMs()) {
                    throw new WorkflowValidationException(
                            WorkflowValidationException.ErrorCode.INVALID_RETRY_POLICY,
                            "step '" + step.id() + "': maxDelayMs (" + retry.maxDelayMs()
                                    + ") must be >= baseDelayMs (" + retry.baseDelayMs() + ")");
                }
            }
        }
    }

    // -- Timeout policy validation -----------------------------------------

    private void validateTimeoutPolicies(WorkflowDefinition definition) {
        for (StepDef step : definition.steps()) {
            TimeoutPolicyDef timeout = step.timeoutPolicy();
            if (timeout != null) {
                if (timeout.timeoutMs() < 100) {
                    throw new WorkflowValidationException(
                            WorkflowValidationException.ErrorCode.INVALID_TIMEOUT_POLICY,
                            "step '" + step.id() + "': timeoutMs must be >= 100, got: "
                                    + timeout.timeoutMs());
                }
                if (timeout.onTimeoutState() != null
                        && !definition.states().contains(timeout.onTimeoutState())) {
                    throw new WorkflowValidationException(
                            WorkflowValidationException.ErrorCode.INVALID_TIMEOUT_POLICY,
                            "step '" + step.id() + "': onTimeoutState '"
                                    + timeout.onTimeoutState() + "' is not in declared states");
                }
            }
        }
    }

    // -- Fork/join validation ----------------------------------------------

    private void validateForkJoinSpecs(WorkflowDefinition definition) {
        List<ForkSpec> specs = definition.forkJoinSpecs();
        if (specs == null || specs.isEmpty()) {
            return;
        }

        Set<String> stepIds = definition.steps().stream()
                .map(StepDef::id)
                .collect(Collectors.toSet());

        for (ForkSpec spec : specs) {
            // Fork step must exist
            if (!stepIds.contains(spec.forkStepId())) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.FORK_JOIN_MISMATCH,
                        "fork step '" + spec.forkStepId() + "' is not defined in steps");
            }

            // Join step must exist
            if (!stepIds.contains(spec.joinStepId())) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.FORK_JOIN_MISMATCH,
                        "join step '" + spec.joinStepId() + "' is not defined in steps");
            }

            // Fork must declare at least one branch name
            if (spec.branchNames() == null || spec.branchNames().isEmpty()) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.FORK_JOIN_MISMATCH,
                        "fork '" + spec.forkStepId() + "' must declare at least one branch name");
            }
        }
    }

    // -- Reachability validation -------------------------------------------

    private void validateReachability(WorkflowDefinition definition, Set<String> reachableStates) {
        for (String state : definition.states()) {
            if (!reachableStates.contains(state)) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.UNREACHABLE_STATE,
                        "state '" + state + "' is unreachable from initialState '"
                                + definition.initialState() + "'");
            }
        }
    }

    // -- Dead-end validation -----------------------------------------------

    private void validateNoDeadEnds(WorkflowDefinition definition, Map<String, Set<String>> adjacencyMap) {
        Set<String> terminalStates = definition.terminalStates();
        for (String state : definition.states()) {
            if (terminalStates.contains(state)) {
                continue;
            }
            Set<String> outgoing = adjacencyMap.get(state);
            if (outgoing == null || outgoing.isEmpty()) {
                throw new WorkflowValidationException(
                        WorkflowValidationException.ErrorCode.DEAD_END_STATE,
                        "non-terminal state '" + state
                                + "' has no outgoing transitions (dead end)");
            }
        }
    }

    // -- Timer uniqueness validation ---------------------------------------

    private void validateTimerUniqueness(WorkflowDefinition definition) {
        Set<String> timerNames = new HashSet<>();
        for (TransitionDef transition : definition.transitions()) {
            TriggerDef trigger = transition.trigger();
            if (trigger != null && "timer".equals(trigger.type())) {
                if (!timerNames.add(trigger.name())) {
                    throw new WorkflowValidationException(
                            WorkflowValidationException.ErrorCode.FORK_JOIN_MISMATCH,
                            "duplicate timer name '" + trigger.name()
                                    + "' in transition '" + transition.id() + "'");
                }
            }
        }
    }

    // -- Graph utilities ---------------------------------------------------

    /**
     * Builds an adjacency map from both steps and transitions.
     * Each entry maps a source state to the set of states reachable in one hop.
     */
    private Map<String, Set<String>> buildAdjacencyMap(WorkflowDefinition definition) {
        Map<String, Set<String>> map = new java.util.HashMap<>();

        for (StepDef step : definition.steps()) {
            map.computeIfAbsent(step.fromState(), k -> new LinkedHashSet<>()).add(step.toState());
        }
        for (TransitionDef transition : definition.transitions()) {
            map.computeIfAbsent(transition.from(), k -> new LinkedHashSet<>()).add(transition.to());
        }

        return map;
    }

    /**
     * Computes the set of all states reachable from the given start state
     * using breadth-first search.
     */
    private Set<String> computeReachableStates(String initialState, Map<String, Set<String>> adjacencyMap) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(initialState);
        visited.add(initialState);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> neighbors = adjacencyMap.getOrDefault(current, Set.of());
            for (String neighbor : neighbors) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return visited;
    }
}
