package io.flowcore.statemachine.dsl;

/**
 * Entry point for the workflow definition DSL.
 *
 * <p>Provides a static factory method to start building a {@link io.flowcore.api.dto.WorkflowDefinition}
 * using a fluent API:
 *
 * <pre>{@code
 * WorkflowDefinition wf = WorkflowDsl.workflow("card-issuance", 1)
 *     .states("NEW", "PROCESSING", "APPROVED", "REJECTED", "COMPLETED")
 *     .initial("NEW")
 *     .terminal("COMPLETED", "REJECTED")
 *     .step("validate")
 *         .fromState("NEW")
 *         .toState("PROCESSING")
 *         .syncActivity("cards", "validateApplication")
 *         .and()
 *     .step("issue-card")
 *         .fromState("PROCESSING")
 *         .toState("APPROVED")
 *         .asyncActivity("cards", "issueCard")
 *         .retry(RetryPolicy.exponential(3, 500, 5000).withJitter(25))
 *         .timeout(TimeoutPolicy.afterMs(30000).onTimeoutTransition("REJECTED"))
 *         .compensateWith("refund")
 *         .and()
 *     .transition("APPROVED", "COMPLETED")
 *         .onEvent("card.delivered")
 *         .and()
 *     .transition("PROCESSING", "REJECTED")
 *         .onEvent("validation.failed")
 *         .guard("#severity == 'critical'")
 *         .and()
 *     .build();
 * }</pre>
 */
public final class WorkflowDsl {

    private WorkflowDsl() {
        // utility class, no instances
    }

    /**
     * Start building a new workflow definition.
     *
     * @param workflowType unique identifier for this workflow type (e.g. "payment.v1")
     * @param version      semantic version number
     * @return a new {@link WorkflowBuilder}
     */
    public static WorkflowBuilder workflow(String workflowType, int version) {
        return new WorkflowBuilder(workflowType, version);
    }
}
