package io.flowcore.statemachine.dsl;

import io.flowcore.api.dto.ForkSpec;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.WorkflowDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fluent builder for constructing a {@link WorkflowDefinition}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * WorkflowDefinition wf = WorkflowDsl.workflow("payment.v1", 1)
 *     .states("PENDING", "APPROVED", "REJECTED", "COMPLETED")
 *     .initial("PENDING")
 *     .terminal("COMPLETED", "REJECTED")
 *     .step("approve")
 *         .fromState("PENDING")
 *         .toState("APPROVED")
 *         .syncActivity("payments", "approve")
 *         .and()
 *     .transition("APPROVED", "COMPLETED")
 *         .onEvent("payment.settled")
 *         .and()
 *     .build();
 * }</pre>
 */
public class WorkflowBuilder {

    private final String workflowType;
    private final String version;

    private final Set<String> states = new LinkedHashSet<>();
    private String initialState;
    private final Set<String> terminalStates = new LinkedHashSet<>();
    private final List<StepDef> steps = new ArrayList<>();
    private final List<TransitionDef> transitions = new ArrayList<>();
    private final List<ForkSpec> forkJoinSpecs = new ArrayList<>();

    WorkflowBuilder(String workflowType, int version) {
        this.workflowType = workflowType;
        this.version = String.valueOf(version);
    }

    /**
     * Declare the set of states for this workflow.
     *
     * @param states state names
     * @return this builder
     */
    public WorkflowBuilder states(String... states) {
        this.states.addAll(Arrays.asList(states));
        return this;
    }

    /**
     * Set the initial state for newly created workflow instances.
     *
     * @param state the initial state name
     * @return this builder
     */
    public WorkflowBuilder initial(String state) {
        this.initialState = state;
        return this;
    }

    /**
     * Declare one or more terminal (end) states for this workflow.
     *
     * @param states terminal state names
     * @return this builder
     */
    public WorkflowBuilder terminal(String... states) {
        this.terminalStates.addAll(Arrays.asList(states));
        return this;
    }

    /**
     * Begin defining a workflow step. Returns a {@link StepBuilder} that
     * will register the step when {@link StepBuilder#and()} is called.
     *
     * @param stepId unique step identifier within the workflow
     * @return a new StepBuilder for this step
     */
    public StepBuilder step(String stepId) {
        return new StepBuilder(stepId, this);
    }

    /**
     * Begin defining a state transition. Returns a {@link TransitionBuilder} that
     * will register the transition when {@link TransitionBuilder#and()} is called.
     *
     * @param from source state
     * @param to   target state
     * @return a new TransitionBuilder for this transition
     */
    public TransitionBuilder transition(String from, String to) {
        return new TransitionBuilder(from, to, this);
    }

    /**
     * Add a fork specification for parallel branch execution.
     *
     * @param forkStepId the step that initiates the fork
     * @return a {@link ForkSpecBuilder} for configuring branches and join
     */
    public ForkSpecBuilder fork(String forkStepId) {
        return new ForkSpecBuilder(forkStepId, this);
    }

    /**
     * Add a join specification for parallel branch convergence.
     *
     * @param joinStepId the step that waits for branches
     * @return a {@link JoinSpecBuilder} for configuring the join
     */
    public JoinSpecBuilder join(String joinStepId) {
        return new JoinSpecBuilder(joinStepId, this);
    }

    /**
     * Register a completed step definition (called internally by StepBuilder).
     *
     * @param step the step to add
     */
    void addStep(StepDef step) {
        this.steps.add(step);
    }

    /**
     * Register a completed transition definition (called internally by TransitionBuilder).
     *
     * @param transition the transition to add
     */
    void addTransition(TransitionDef transition) {
        this.transitions.add(transition);
    }

    /**
     * Register a fork/join specification (called internally by ForkSpecBuilder / JoinSpecBuilder).
     *
     * @param spec the ForkSpec to add
     */
    void addForkJoinSpec(ForkSpec spec) {
        this.forkJoinSpecs.add(spec);
    }

    /**
     * Build the final {@link WorkflowDefinition} from all accumulated configuration.
     *
     * @return a new WorkflowDefinition
     * @throws IllegalStateException if required fields are missing
     */
    public WorkflowDefinition build() {
        if (workflowType == null || workflowType.isBlank()) {
            throw new IllegalStateException("workflowType is required");
        }
        if (initialState == null || initialState.isBlank()) {
            throw new IllegalStateException("initial state is required");
        }
        if (states.isEmpty()) {
            throw new IllegalStateException("at least one state is required");
        }

        return new WorkflowDefinition(
                workflowType,
                version,
                new HashSet<>(states),
                initialState,
                new HashSet<>(terminalStates),
                List.copyOf(steps),
                List.copyOf(transitions),
                forkJoinSpecs.isEmpty() ? null : List.copyOf(forkJoinSpecs)
        );
    }

    // -----------------------------------------------------------------------
    // Inner builders for fork/join specs
    // -----------------------------------------------------------------------

    /**
     * Builder for constructing a fork specification with branches.
     */
    public static class ForkSpecBuilder {

        private final String forkStepId;
        private final WorkflowBuilder parent;
        private final List<String> branchNames = new ArrayList<>();

        ForkSpecBuilder(String forkStepId, WorkflowBuilder parent) {
            this.forkStepId = forkStepId;
            this.parent = parent;
        }

        /**
         * Add a branch name to this fork.
         *
         * @param branchName the branch name
         * @return this builder
         */
        public ForkSpecBuilder branch(String branchName) {
            this.branchNames.add(branchName);
            return this;
        }

        /**
         * Add multiple branch names to this fork.
         *
         * @param names branch names
         * @return this builder
         */
        public ForkSpecBuilder branches(String... names) {
            this.branchNames.addAll(Arrays.asList(names));
            return this;
        }

        /**
         * Return to the parent {@link WorkflowBuilder}.
         *
         * @return the parent WorkflowBuilder
         */
        public WorkflowBuilder and() {
            return parent;
        }

        /**
         * Get the fork step id.
         *
         * @return fork step id
         */
        String forkStepId() {
            return forkStepId;
        }

        /**
         * Get the accumulated branch names.
         *
         * @return branch names
         */
        List<String> branchNames() {
            return List.copyOf(branchNames);
        }
    }

    /**
     * Builder for constructing a join specification.
     */
    public static class JoinSpecBuilder {

        private final String joinStepId;
        private final WorkflowBuilder parent;

        private String forkStepId;
        private final List<String> branchNames = new ArrayList<>();
        private String joinPolicy = "ALL";

        JoinSpecBuilder(String joinStepId, WorkflowBuilder parent) {
            this.joinStepId = joinStepId;
            this.parent = parent;
        }

        /**
         * Set the fork step this join is paired with.
         *
         * @param forkStepId the fork step identifier
         * @return this builder
         */
        public JoinSpecBuilder forFork(String forkStepId) {
            this.forkStepId = forkStepId;
            return this;
        }

        /**
         * Add a branch name to this join.
         *
         * @param branchName the branch name
         * @return this builder
         */
        public JoinSpecBuilder branch(String branchName) {
            this.branchNames.add(branchName);
            return this;
        }

        /**
         * Add multiple branch names to this join.
         *
         * @param names branch names
         * @return this builder
         */
        public JoinSpecBuilder branches(String... names) {
            this.branchNames.addAll(Arrays.asList(names));
            return this;
        }

        /**
         * Set the join policy to wait for ALL branches.
         *
         * @return this builder
         */
        public JoinSpecBuilder waitForAll() {
            this.joinPolicy = "ALL";
            return this;
        }

        /**
         * Set the join policy to wait for ANY branch.
         *
         * @return this builder
         */
        public JoinSpecBuilder waitForAny() {
            this.joinPolicy = "ANY";
            return this;
        }

        /**
         * Return to the parent {@link WorkflowBuilder}, registering the fork/join spec.
         *
         * @return the parent WorkflowBuilder
         */
        public WorkflowBuilder and() {
            ForkSpec spec = new ForkSpec(forkStepId, List.copyOf(branchNames), joinStepId, joinPolicy);
            parent.addForkJoinSpec(spec);
            return parent;
        }
    }
}
