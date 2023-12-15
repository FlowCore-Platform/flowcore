package io.flowcore.obs.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FlowcoreMdcInjector} verifying that MDC keys are correctly
 * set and cleared.
 */
class FlowcoreMdcInjectorTest {

    @BeforeEach
    void ensureCleanSlate() {
        MDC.clear();
    }

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Nested
    @DisplayName("setWorkflowContext")
    class SetWorkflowContext {

        @Test
        @DisplayName("sets all three workflow MDC keys")
        void setsAllKeys() {
            UUID instanceId = UUID.randomUUID();
            FlowcoreMdcInjector.setWorkflowContext("card-issuance", instanceId, "order-123");

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_TYPE)).isEqualTo("card-issuance");
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_INSTANCE_ID)).isEqualTo(instanceId.toString());
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_BUSINESS_KEY)).isEqualTo("order-123");
        }

        @Test
        @DisplayName("does not set business_key when null")
        void nullBusinessKey() {
            UUID instanceId = UUID.randomUUID();
            FlowcoreMdcInjector.setWorkflowContext("card-issuance", instanceId, null);

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_TYPE)).isEqualTo("card-issuance");
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_INSTANCE_ID)).isEqualTo(instanceId.toString());
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_BUSINESS_KEY)).isNull();
        }
    }

    @Nested
    @DisplayName("setCommandContext")
    class SetCommandContext {

        @Test
        @DisplayName("sets command name and source")
        void setsKeys() {
            FlowcoreMdcInjector.setCommandContext("StartWorkflow", "api");

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_COMMAND)).isEqualTo("StartWorkflow");
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_COMMAND_SOURCE)).isEqualTo("api");
        }
    }

    @Nested
    @DisplayName("setStepContext")
    class SetStepContext {

        @Test
        @DisplayName("sets step id and attempt")
        void setsKeys() {
            FlowcoreMdcInjector.setStepContext("validate-pin", 3);

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_STEP_ID)).isEqualTo("validate-pin");
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_STEP_ATTEMPT)).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("setProviderContext")
    class SetProviderContext {

        @Test
        @DisplayName("sets provider and operation")
        void setsKeys() {
            FlowcoreMdcInjector.setProviderContext("bank-gateway", "createAccount");

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_PROVIDER)).isEqualTo("bank-gateway");
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_PROVIDER_OPERATION)).isEqualTo("createAccount");
        }
    }

    @Nested
    @DisplayName("clearAll")
    class ClearAll {

        @Test
        @DisplayName("removes all Flowcore MDC keys")
        void removesAllKeys() {
            FlowcoreMdcInjector.setWorkflowContext("wf", UUID.randomUUID(), "bk");
            FlowcoreMdcInjector.setCommandContext("cmd", "src");
            FlowcoreMdcInjector.setStepContext("step", 1);
            FlowcoreMdcInjector.setProviderContext("prov", "op");

            FlowcoreMdcInjector.clearAll();

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_TYPE)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_INSTANCE_ID)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_BUSINESS_KEY)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_COMMAND)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_COMMAND_SOURCE)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_STEP_ID)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_STEP_ATTEMPT)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_PROVIDER)).isNull();
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_PROVIDER_OPERATION)).isNull();
        }

        @Test
        @DisplayName("clearAll is idempotent when called multiple times")
        void idempotent() {
            FlowcoreMdcInjector.setWorkflowContext("wf", UUID.randomUUID(), "bk");
            FlowcoreMdcInjector.clearAll();
            FlowcoreMdcInjector.clearAll();

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_TYPE)).isNull();
        }
    }

    @Test
    @DisplayName("full workflow lifecycle sets and clears context correctly")
    void fullLifecycle() {
        UUID instanceId = UUID.randomUUID();

        try {
            FlowcoreMdcInjector.setWorkflowContext("card-issuance", instanceId, "order-42");
            FlowcoreMdcInjector.setStepContext("validate-pin", 1);

            assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_TYPE)).isEqualTo("card-issuance");
            assertThat(MDC.get(FlowcoreMdcInjector.MDC_STEP_ID)).isEqualTo("validate-pin");
        } finally {
            FlowcoreMdcInjector.clearAll();
        }

        assertThat(MDC.get(FlowcoreMdcInjector.MDC_WORKFLOW_TYPE)).isNull();
        assertThat(MDC.get(FlowcoreMdcInjector.MDC_STEP_ID)).isNull();
    }
}
