package io.flowcore.demo.card.workflow;

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
 * Defines and registers the card issuance workflow.
 *
 * <p>State machine: INIT -> KYC_PENDING -> KYC_APPROVED -> CARD_PROVISIONING -> WALLET_BINDING -> ACTIVE
 * with error path to FAILED at multiple stages.
 */
@Component
public class CardIssuanceWorkflow {

    private static final Logger log = LoggerFactory.getLogger(CardIssuanceWorkflow.class);

    public static final String WORKFLOW_TYPE = "demo.card.issuance";

    // Event types
    public static final String EVENT_KYC_RESULT = "KYC_RESULT";
    public static final String EVENT_WALLET_BOUND = "WALLET_BOUND";

    private final WorkflowRegistry registry;

    public CardIssuanceWorkflow(WorkflowRegistry registry) {
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
                .states("INIT", "KYC_PENDING", "KYC_APPROVED", "CARD_PROVISIONING",
                        "WALLET_BINDING", "ACTIVE", "FAILED")
                .initial("INIT")
                .terminal("ACTIVE", "FAILED")
                // Step 1: KYC verification
                .step("kycCheck")
                    .fromState("INIT")
                    .toState("KYC_PENDING")
                    .asyncActivity("KycProviderAdapter", "verify")
                    .retry(RetryPolicy.exponential(5, 200, 5000))
                    .timeout(TimeoutPolicy.afterMs(10000).onTimeoutTransition("FAILED"))
                    .and()
                // Step 2: provision virtual card
                .step("provisionCard")
                    .fromState("KYC_APPROVED")
                    .toState("CARD_PROVISIONING")
                    .asyncActivity("CardIssuerAdapter", "provisionVirtualCard")
                    .retry(RetryPolicy.fixed(3, 500))
                    .and()
                // Step 3: bind card to wallet
                .step("bindWallet")
                    .fromState("CARD_PROVISIONING")
                    .toState("WALLET_BINDING")
                    .asyncActivity("WalletAdapter", "bindCard")
                    .retry(RetryPolicy.fixed(3, 300))
                    .and()
                // Transition: KYC approved on successful result
                .transition("KYC_PENDING", "KYC_APPROVED")
                    .onEvent(EVENT_KYC_RESULT)
                    .guard("$.kyc.status == \"APPROVED\"")
                    .and()
                // Transition: KYC failed on unsuccessful result
                .transition("KYC_PENDING", "FAILED")
                    .onEvent(EVENT_KYC_RESULT)
                    .guard("$.kyc.status != \"APPROVED\"")
                    .and()
                // Transition: wallet binding complete
                .transition("WALLET_BINDING", "ACTIVE")
                    .onEvent(EVENT_WALLET_BOUND)
                    .and()
                .build();
    }
}
