package io.flowcore.statemachine.dsl;

import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.TriggerDef;

/**
 * Fluent builder for constructing a single {@link TransitionDef} within a workflow definition.
 *
 * <p>Typical usage:
 * <pre>{@code
 * .transition("PENDING", "APPROVED")
 *     .onEvent("payment.received")
 *     .guard("#amount > 100")
 *     .and()
 * }</pre>
 */
public class TransitionBuilder {

    private final String from;
    private final String to;
    private final WorkflowBuilder parent;

    private TriggerDef trigger;
    private String guardExpression;

    TransitionBuilder(String from, String to, WorkflowBuilder parent) {
        this.from = from;
        this.to = to;
        this.parent = parent;
    }

    /**
     * Set the trigger for this transition to an event with the given name.
     *
     * @param eventName the event name that triggers this transition
     * @return this builder
     */
    public TransitionBuilder onEvent(String eventName) {
        this.trigger = new TriggerDef("event", eventName);
        return this;
    }

    /**
     * Set the trigger for this transition to a timer with the given name.
     *
     * @param timerName the timer name that triggers this transition
     * @return this builder
     */
    public TransitionBuilder onTimer(String timerName) {
        this.trigger = new TriggerDef("timer", timerName);
        return this;
    }

    /**
     * Set a guard (conditional) expression for this transition.
     *
     * @param expression the guard expression (e.g. SpEL)
     * @return this builder
     */
    public TransitionBuilder guard(String expression) {
        this.guardExpression = expression;
        return this;
    }

    /**
     * Return to the parent {@link WorkflowBuilder}, registering this transition.
     *
     * @return the parent WorkflowBuilder
     */
    public WorkflowBuilder and() {
        parent.addTransition(build());
        return parent;
    }

    /**
     * Build the {@link TransitionDef} from the accumulated configuration.
     * <p>
     * The transition id is generated as "from-&gt;to" by default.
     *
     * @return a new TransitionDef
     */
    TransitionDef build() {
        String id = from + "->" + to;
        return new TransitionDef(id, from, to, trigger, guardExpression);
    }
}
