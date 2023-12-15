package io.flowcore.statemachine.compiler;

import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.WorkflowDefinition;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An optimized, compiled representation of a {@link WorkflowDefinition} ready for execution.
 *
 * <p>Pre-computes adjacency maps, step indices, and transition lookups
 * to avoid repeated traversals of the definition at runtime.</p>
 *
 * <p>This class is immutable and thread-safe.</p>
 */
public class CompiledWorkflow {

    private final WorkflowDefinition definition;
    private final Map<String, Set<String>> adjacencyMap;
    private final Map<String, StepDef> stepIndex;
    private final Map<String, List<TransitionDef>> transitionsByFromState;
    private final Set<String> reachableStates;

    /**
     * Creates a new compiled workflow.
     *
     * @param definition             the source workflow definition
     * @param adjacencyMap           state -> set of directly reachable states
     * @param stepIndex              stepId -> step definition
     * @param transitionsByFromState fromState -> list of transitions from that state
     * @param reachableStates        all states reachable from the initial state
     */
    public CompiledWorkflow(
            WorkflowDefinition definition,
            Map<String, Set<String>> adjacencyMap,
            Map<String, StepDef> stepIndex,
            Map<String, List<TransitionDef>> transitionsByFromState,
            Set<String> reachableStates
    ) {
        this.definition = definition;
        this.adjacencyMap = Collections.unmodifiableMap(
                deepCopyAdjacencyMap(adjacencyMap));
        this.stepIndex = Collections.unmodifiableMap(new HashMap<>(stepIndex));
        this.transitionsByFromState = Collections.unmodifiableMap(
                deepCopyTransitionsMap(transitionsByFromState));
        this.reachableStates = Collections.unmodifiableSet(new LinkedHashSet<>(reachableStates));
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public Map<String, Set<String>> getAdjacencyMap() {
        return adjacencyMap;
    }

    public Map<String, StepDef> getStepIndex() {
        return stepIndex;
    }

    public Map<String, List<TransitionDef>> getTransitionsByFromState() {
        return transitionsByFromState;
    }

    public Set<String> getReachableStates() {
        return reachableStates;
    }

    private static Map<String, Set<String>> deepCopyAdjacencyMap(Map<String, Set<String>> original) {
        Map<String, Set<String>> copy = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : original.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return copy;
    }

    private static Map<String, List<TransitionDef>> deepCopyTransitionsMap(
            Map<String, List<TransitionDef>> original) {
        Map<String, List<TransitionDef>> copy = new HashMap<>();
        for (Map.Entry<String, List<TransitionDef>> entry : original.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new java.util.ArrayList<>(entry.getValue())));
        }
        return copy;
    }
}
