package io.flowcore.obs.logging;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Injects and removes structured logging context values into SLF4J {@link MDC}.
 * <p>
 * Use the static {@code set*} methods at entry points (interceptors, AOP
 * advice, or directly in service code) and always pair them with
 * {@link #clearAll()} in a {@code finally} block.
 *
 * <pre>{@code
 * try {
 *     FlowcoreMdcInjector.setWorkflowContext("card-issuance", instanceId, bizKey);
 *     FlowcoreMdcInjector.setStepContext("validate-pin", 2);
 *     // ... business logic ...
 * } finally {
 *     FlowcoreMdcInjector.clearAll();
 * }
 * }</pre>
 */
@Component
public class FlowcoreMdcInjector {

    // ── MDC key constants ────────────────────────────────────────────────

    public static final String MDC_WORKFLOW_TYPE = "workflow.type";
    public static final String MDC_WORKFLOW_INSTANCE_ID = "workflow.instance_id";
    public static final String MDC_BUSINESS_KEY = "business_key";
    public static final String MDC_COMMAND = "command.name";
    public static final String MDC_COMMAND_SOURCE = "command.source";
    public static final String MDC_STEP_ID = "step.id";
    public static final String MDC_STEP_ATTEMPT = "step.attempt";
    public static final String MDC_PROVIDER = "provider.name";
    public static final String MDC_PROVIDER_OPERATION = "provider.operation";

    /**
     * Sets workflow-level context values in MDC.
     *
     * @param workflowType the workflow definition type
     * @param instanceId   the unique workflow instance identifier
     * @param businessKey  the application-level correlation key (may be null)
     */
    public static void setWorkflowContext(String workflowType, UUID instanceId, String businessKey) {
        MDC.put(MDC_WORKFLOW_TYPE, workflowType);
        MDC.put(MDC_WORKFLOW_INSTANCE_ID, instanceId.toString());
        if (businessKey != null) {
            MDC.put(MDC_BUSINESS_KEY, businessKey);
        }
    }

    /**
     * Sets command-level context values in MDC.
     *
     * @param command the command name being processed
     * @param source  the origin of the command (e.g. "api", "kafka", "timer")
     */
    public static void setCommandContext(String command, String source) {
        MDC.put(MDC_COMMAND, command);
        MDC.put(MDC_COMMAND_SOURCE, source);
    }

    /**
     * Sets step-level context values in MDC.
     *
     * @param stepId   the step identifier within the workflow definition
     * @param attempt  the current retry attempt number (1-based)
     */
    public static void setStepContext(String stepId, int attempt) {
        MDC.put(MDC_STEP_ID, stepId);
        MDC.put(MDC_STEP_ATTEMPT, String.valueOf(attempt));
    }

    /**
     * Sets external provider call context values in MDC.
     *
     * @param provider  the provider name (e.g. "bank-gateway", "card-processor")
     * @param operation the operation being invoked (e.g. "createAccount")
     */
    public static void setProviderContext(String provider, String operation) {
        MDC.put(MDC_PROVIDER, provider);
        MDC.put(MDC_PROVIDER_OPERATION, operation);
    }

    /**
     * Removes all Flowcore MDC keys. Call this in a {@code finally} block
     * to prevent context leakage across thread-bound request boundaries.
     */
    public static void clearAll() {
        MDC.remove(MDC_WORKFLOW_TYPE);
        MDC.remove(MDC_WORKFLOW_INSTANCE_ID);
        MDC.remove(MDC_BUSINESS_KEY);
        MDC.remove(MDC_COMMAND);
        MDC.remove(MDC_COMMAND_SOURCE);
        MDC.remove(MDC_STEP_ID);
        MDC.remove(MDC_STEP_ATTEMPT);
        MDC.remove(MDC_PROVIDER);
        MDC.remove(MDC_PROVIDER_OPERATION);
    }
}
