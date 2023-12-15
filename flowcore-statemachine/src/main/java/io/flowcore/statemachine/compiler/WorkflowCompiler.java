package io.flowcore.statemachine.compiler;

import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.WorkflowDefinition;
import io.flowcore.statemachine.validation.WorkflowValidator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a {@link WorkflowDefinition} into a {@link CompiledWorkflow}
 * (optimized execution graph).
 *
 * <p>The compilation pipeline:</p>
 * <ol>
 *   <li>Validate the definition using {@link WorkflowValidator}</li>
 *   <li>Build an adjacency map (state -> reachable states)</li>
 *   <li>Index steps by ID for O(1) lookup</li>
 *   <li>Group transitions by source state</li>
 *   <li>Compute reachable state set via BFS</li>
 * </ol>
 *
 * <p>This class is thread-safe.</p>
 */
public class WorkflowCompiler {

    private final WorkflowValidator validator;

    /**
     * Creates a new compiler with a default validator.
     */
    public WorkflowCompiler() {
        this.validator = new WorkflowValidator();
    }

    /**
     * Creates a new compiler with a custom validator.
     *
     * @param validator the validator to use
     */
    public WorkflowCompiler(WorkflowValidator validator) {
        this.validator = validator;
    }

    /**
     * Validates and compiles the given workflow definition into an optimized
     * execution graph.
     *
     * @param definition the workflow definition to compile
     * @return a compiled workflow ready for execution
     * @throws io.flowcore.statemachine.validation.WorkflowValidationException if validation fails
     */
    public CompiledWorkflow compile(WorkflowDefinition definition) {
        // Step 1: Validate
        validator.validate(definition);

        // Step 2: Build adjacency map
        Map<String, Set<String>> adjacencyMap = buildAdjacencyMap(definition);

        // Step 3: Index steps by ID
        Map<String, StepDef> stepIndex = buildStepIndex(definition);

        // Step 4: Group transitions by source state
        Map<String, List<TransitionDef>> transitionsByFromState = buildTransitionsByFromState(definition);

        // Step 5: Compute reachable states
        Set<String> reachableStates = computeReachableStates(definition.initialState(), adjacencyMap);

        return new CompiledWorkflow(definition, adjacencyMap, stepIndex, transitionsByFromState, reachableStates);
    }

    // -- Adjacency map -----------------------------------------------------

    private Map<String, Set<String>> buildAdjacencyMap(WorkflowDefinition definition) {
        Map<String, Set<String>> map = new HashMap<>();

        for (StepDef step : definition.steps()) {
            map.computeIfAbsent(step.fromState(), k -> new LinkedHashSet<>()).add(step.toState());
        }
        for (TransitionDef transition : definition.transitions()) {
            map.computeIfAbsent(transition.from(), k -> new LinkedHashSet<>()).add(transition.to());
        }

        return map;
    }

    // -- Step index --------------------------------------------------------

    private Map<String, StepDef> buildStepIndex(WorkflowDefinition definition) {
        Map<String, StepDef> index = new HashMap<>();
        for (StepDef step : definition.steps()) {
            index.put(step.id(), step);
        }
        return index;
    }

    // -- Transitions by source state ---------------------------------------

    private Map<String, List<TransitionDef>> buildTransitionsByFromState(WorkflowDefinition definition) {
        Map<String, List<TransitionDef>> map = new HashMap<>();
        for (TransitionDef transition : definition.transitions()) {
            map.computeIfAbsent(transition.from(), k -> new ArrayList<>()).add(transition);
        }
        return map;
    }

    // -- Reachable states via BFS ------------------------------------------

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
