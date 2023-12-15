package io.flowcore.demo.payment.workflow;

import io.flowcore.api.WorkflowRegistry;
import io.flowcore.api.dto.WorkflowDefinition;
import io.flowcore.statemachine.dsl.RetryPolicy;
import io.flowcore.statemachine.dsl.TimeoutPolicy;
import io.flowcore.statemachine.dsl.WorkflowDsl;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Defines and registers the payment lifecycle workflow.
 *
 * <p>State machine: INITIATED -> AUTHORIZED -> CAPTURED -> SETTLED
 * with error path to FAILED and refund path to REFUNDED.
 */
@Component
public class PaymentWorkflow {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorkflow.class);

    public static final String WORKFLOW_TYPE = "demo.payment.lifecycle";
    public static final String EVENT_CAPTURE = "CAPTURE";
    public static final String EVENT_REFUND_REQUEST = "REFUND_REQUEST";
    public static final String EVENT_AUTH_RESULT = "AUTH_RESULT";

    private final WorkflowRegistry registry;

    public PaymentWorkflow(WorkflowRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void register() {
        WorkflowDefinition definition = buildDefinition();
        registry.register(definition);
        log.info("Registered workflow definition: type={}, version={}",
                definition.workflowType(), definition.version());
    }

    WorkflowDefinition buildDefinition() {
        return WorkflowDsl.workflow(WORKFLOW_TYPE, 1)
                .states("INITIATED", "AUTHORIZED", "CAPTURED", "SETTLED", "REFUNDED", "FAILED")
                .initial("INITIATED")
                .terminal("SETTLED", "REFUNDED", "FAILED")
                // Step 1: authorize payment
                .step("authorizePayment")
                    .fromState("INITIATED")
                    .toState("AUTHORIZED")
                    .asyncActivity("PaymentGatewayAdapter", "authorize")
                    .retry(RetryPolicy.exponential(3, 300, 5000))
                    .timeout(TimeoutPolicy.afterMs(15000).onTimeoutTransition("FAILED"))
                    .and()
                // Step 2: capture payment
                .step("capturePayment")
                    .fromState("AUTHORIZED")
                    .toState("CAPTURED")
                    .asyncActivity("PaymentGatewayAdapter", "capture")
                    .retry(RetryPolicy.fixed(3, 500))
                    .and()
                // Step 3: settle payment
                .step("settlePayment")
                    .fromState("CAPTURED")
                    .toState("SETTLED")
                    .asyncActivity("SettlementAdapter", "settle")
                    .retry(RetryPolicy.fixed(3, 1000))
                    .and()
                // Transition: captured -> refunded on refund request
                .transition("CAPTURED", "REFUNDED")
                    .onEvent(EVENT_REFUND_REQUEST)
                    .and()
                // Transition: settled -> refunded on refund request
                .transition("SETTLED", "REFUNDED")
                    .onEvent(EVENT_REFUND_REQUEST)
                    .and()
                // Transition: authorized -> failed on failed auth result
                .transition("AUTHORIZED", "FAILED")
                    .onEvent(EVENT_AUTH_RESULT)
                    .guard("$.auth.status != \"SUCCESS\"")
                    .and()
                .build();
    }
}
