package io.flowcore.statemachine.dsl;

import io.flowcore.api.dto.ActivityDef;
import io.flowcore.api.dto.CompensationDef;
import io.flowcore.api.dto.RetryPolicyDef;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TimeoutPolicyDef;

/**
 * Fluent builder for constructing a single {@link StepDef} within a workflow definition.
 *
 * <p>Typical usage:
 * <pre>{@code
 * .step("charge-card")
 *     .fromState("READY")
 *     .toState("CHARGED")
 *     .asyncActivity("stripe", "createCharge")
 *     .retry(RetryPolicy.exponential(3, 500, 5000))
 *     .and()
 * }</pre>
 */
public class StepBuilder {

    private final String stepId;
    private final WorkflowBuilder parent;

    private String fromState;
    private String toState;
    private String kind;
    private ActivityDef activity;
    private RetryPolicyDef retryPolicyDef;
    private TimeoutPolicyDef timeoutPolicyDef;
    private CompensationDef compensationDef;

    StepBuilder(String stepId, WorkflowBuilder parent) {
        this.stepId = stepId;
        this.parent = parent;
    }

    /**
     * Set the source state for this step.
     *
     * @param state source state name
     * @return this builder
     */
    public StepBuilder fromState(String state) {
        this.fromState = state;
        return this;
    }

    /**
     * Set the target state for this step.
     *
     * @param state target state name
     * @return this builder
     */
    public StepBuilder toState(String state) {
        this.toState = state;
        return this;
    }

    /**
     * Configure this step as an asynchronous activity.
     *
     * @param adapter   the provider adapter name
     * @param operation the operation within the adapter
     * @return this builder
     */
    public StepBuilder asyncActivity(String adapter, String operation) {
        this.kind = "asyncActivity";
        this.activity = new ActivityDef(adapter, operation);
        return this;
    }

    /**
     * Configure this step as a synchronous activity.
     *
     * @param adapter   the provider adapter name
     * @param operation the operation within the adapter
     * @return this builder
     */
    public StepBuilder syncActivity(String adapter, String operation) {
        this.kind = "syncActivity";
        this.activity = new ActivityDef(adapter, operation);
        return this;
    }

    /**
     * Set the retry policy for this step.
     *
     * @param retryPolicy DSL retry policy
     * @return this builder
     */
    public StepBuilder retry(RetryPolicy retryPolicy) {
        this.retryPolicyDef = retryPolicy.toApiDto();
        return this;
    }

    /**
     * Set the timeout policy for this step.
     *
     * @param timeoutPolicy DSL timeout policy
     * @return this builder
     */
    public StepBuilder timeout(TimeoutPolicy timeoutPolicy) {
        this.timeoutPolicyDef = timeoutPolicy.toApiDto();
        return this;
    }

    /**
     * Configure a compensation step to run if this step needs to be rolled back.
     *
     * @param compensationStepId the step id of the compensation activity
     * @return this builder
     */
    public StepBuilder compensateWith(String compensationStepId) {
        this.compensationDef = new CompensationDef("compensate", new ActivityDef(compensationStepId, compensationStepId));
        return this;
    }

    /**
     * Return to the parent {@link WorkflowBuilder}, registering this step.
     *
     * @return the parent WorkflowBuilder
     */
    public WorkflowBuilder and() {
        parent.addStep(build());
        return parent;
    }

    /**
     * Build the {@link StepDef} from the accumulated configuration.
     *
     * @return a new StepDef
     */
    StepDef build() {
        return new StepDef(
                stepId,
                kind,
                fromState,
                toState,
                activity,
                retryPolicyDef,
                timeoutPolicyDef,
                compensationDef
        );
    }
}
