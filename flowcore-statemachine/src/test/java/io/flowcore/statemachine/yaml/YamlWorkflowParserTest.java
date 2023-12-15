package io.flowcore.statemachine.yaml;

import io.flowcore.api.dto.ActivityDef;
import io.flowcore.api.dto.CompensationDef;
import io.flowcore.api.dto.ForkSpec;
import io.flowcore.api.dto.RetryPolicyDef;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TimeoutPolicyDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.TriggerDef;
import io.flowcore.api.dto.WorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlWorkflowParserTest {

    private YamlWorkflowParser parser;

    @BeforeEach
    void setUp() {
        parser = new YamlWorkflowParser();
    }

    // =====================================================================
    // Complete card issuance workflow
    // =====================================================================

    @Nested
    @DisplayName("Complete card issuance workflow parsing")
    class CardIssuanceTests {

        @Test
        @DisplayName("parses complete card issuance workflow from string")
        void parseCompleteCardIssuanceWorkflow() {
            String yaml = """
                    workflowType: "demo.card.issuance"
                    version: 1
                    states: [INIT, KYC_PENDING, KYC_APPROVED, CARD_PROVISIONING, WALLET_BINDING, ACTIVE, FAILED]
                    initialState: INIT
                    terminalStates: [ACTIVE, FAILED]

                    steps:
                      - id: kycCheck
                        kind: asyncActivity
                        from: INIT
                        to: KYC_PENDING
                        activity:
                          adapter: KycProviderAdapter
                          operation: verify
                        retry:
                          mode: exponential
                          maxAttempts: 5
                          baseDelayMs: 200
                          maxDelayMs: 5000
                          jitterPct: 20
                        timeout:
                          timeoutMs: 10000
                          onTimeoutState: FAILED
                        compensation:
                          kind: asyncActivity
                          activity:
                            adapter: KycProviderAdapter
                            operation: cancelVerification

                    transitions:
                      - id: kycApproved
                        from: KYC_PENDING
                        to: KYC_APPROVED
                        trigger:
                          type: event
                          name: KYC_RESULT
                        guard: '$.kyc.status == "APPROVED"'
                    """;

            WorkflowDefinition def = parser.parse(yaml);

            // -- Basic fields
            assertThat(def.workflowType()).isEqualTo("demo.card.issuance");
            assertThat(def.version()).isEqualTo("1");
            assertThat(def.initialState()).isEqualTo("INIT");
            assertThat(def.states()).containsExactlyInAnyOrder(
                    "INIT", "KYC_PENDING", "KYC_APPROVED",
                    "CARD_PROVISIONING", "WALLET_BINDING", "ACTIVE", "FAILED");
            assertThat(def.terminalStates()).containsExactlyInAnyOrder("ACTIVE", "FAILED");

            // -- Steps
            assertThat(def.steps()).hasSize(1);
            StepDef kycStep = def.steps().get(0);
            assertThat(kycStep.id()).isEqualTo("kycCheck");
            assertThat(kycStep.kind()).isEqualTo("asyncActivity");
            assertThat(kycStep.fromState()).isEqualTo("INIT");
            assertThat(kycStep.toState()).isEqualTo("KYC_PENDING");

            // Activity
            assertThat(kycStep.activity()).isNotNull();
            assertThat(kycStep.activity().adapter()).isEqualTo("KycProviderAdapter");
            assertThat(kycStep.activity().operation()).isEqualTo("verify");

            // Retry policy
            assertThat(kycStep.retryPolicy()).isNotNull();
            RetryPolicyDef retry = kycStep.retryPolicy();
            assertThat(retry.mode()).isEqualTo("exponential");
            assertThat(retry.maxAttempts()).isEqualTo(5);
            assertThat(retry.baseDelayMs()).isEqualTo(200);
            assertThat(retry.maxDelayMs()).isEqualTo(5000);
            assertThat(retry.jitterPct()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.001));

            // Timeout policy
            assertThat(kycStep.timeoutPolicy()).isNotNull();
            TimeoutPolicyDef timeout = kycStep.timeoutPolicy();
            assertThat(timeout.timeoutMs()).isEqualTo(10000);
            assertThat(timeout.onTimeoutState()).isEqualTo("FAILED");

            // Compensation
            assertThat(kycStep.compensation()).isNotNull();
            CompensationDef compensation = kycStep.compensation();
            assertThat(compensation.kind()).isEqualTo("asyncActivity");
            assertThat(compensation.activity()).isNotNull();
            assertThat(compensation.activity().adapter()).isEqualTo("KycProviderAdapter");
            assertThat(compensation.activity().operation()).isEqualTo("cancelVerification");

            // -- Transitions
            assertThat(def.transitions()).hasSize(1);
            TransitionDef transition = def.transitions().get(0);
            assertThat(transition.id()).isEqualTo("kycApproved");
            assertThat(transition.from()).isEqualTo("KYC_PENDING");
            assertThat(transition.to()).isEqualTo("KYC_APPROVED");
            assertThat(transition.trigger()).isNotNull();
            assertThat(transition.trigger().type()).isEqualTo("event");
            assertThat(transition.trigger().name()).isEqualTo("KYC_RESULT");
            assertThat(transition.guardExpression()).isEqualTo("$.kyc.status == \"APPROVED\"");
        }

        @Test
        @DisplayName("parses complete workflow from InputStream")
        void parseFromInputStream() {
            String yaml = """
                    workflowType: "test.wf"
                    version: 2
                    states: [A, B]
                    initialState: A
                    terminalStates: [B]
                    steps:
                      - id: s1
                        kind: task
                        from: A
                        to: B
                    transitions: []
                    """;

            ByteArrayInputStream is = new ByteArrayInputStream(
                    yaml.getBytes(StandardCharsets.UTF_8));
            WorkflowDefinition def = parser.parse(is);

            assertThat(def.workflowType()).isEqualTo("test.wf");
            assertThat(def.version()).isEqualTo("2");
            assertThat(def.steps()).hasSize(1);
        }
    }

    // =====================================================================
    // Minimal workflow
    // =====================================================================

    @Nested
    @DisplayName("Minimal workflow parsing")
    class MinimalWorkflowTests {

        @Test
        @DisplayName("parses minimal workflow with no steps or transitions")
        void parseMinimalWorkflow() {
            String yaml = """
                    workflowType: "minimal.wf"
                    version: 1
                    states: [INIT, DONE]
                    initialState: INIT
                    terminalStates: [DONE]
                    """;

            WorkflowDefinition def = parser.parse(yaml);

            assertThat(def.workflowType()).isEqualTo("minimal.wf");
            assertThat(def.version()).isEqualTo("1");
            assertThat(def.states()).containsExactlyInAnyOrder("INIT", "DONE");
            assertThat(def.initialState()).isEqualTo("INIT");
            assertThat(def.terminalStates()).containsExactlyInAnyOrder("DONE");
            assertThat(def.steps()).isEmpty();
            assertThat(def.transitions()).isEmpty();
            assertThat(def.forkJoinSpecs()).isNull();
        }

        @Test
        @DisplayName("parses workflow with steps but no optional fields")
        void parseWorkflowWithMinimalStep() {
            String yaml = """
                    workflowType: "test.simple"
                    version: 1
                    states: [START, END]
                    initialState: START
                    terminalStates: [END]
                    steps:
                      - id: go
                        kind: task
                        from: START
                        to: END
                    """;

            WorkflowDefinition def = parser.parse(yaml);

            assertThat(def.steps()).hasSize(1);
            StepDef step = def.steps().get(0);
            assertThat(step.id()).isEqualTo("go");
            assertThat(step.kind()).isEqualTo("task");
            assertThat(step.fromState()).isEqualTo("START");
            assertThat(step.toState()).isEqualTo("END");
            assertThat(step.activity()).isNull();
            assertThat(step.retryPolicy()).isNull();
            assertThat(step.timeoutPolicy()).isNull();
            assertThat(step.compensation()).isNull();
        }
    }

    // =====================================================================
    // Invalid YAML
    // =====================================================================

    @Nested
    @DisplayName("Invalid YAML handling")
    class InvalidYamlTests {

        @Test
        @DisplayName("throws on null/empty YAML")
        void throwsOnNullYaml() {
            assertThatThrownBy(() -> parser.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on missing workflowType")
        void throwsOnMissingWorkflowType() {
            String yaml = """
                    version: 1
                    states: [A, B]
                    initialState: A
                    terminalStates: [B]
                    """;

            assertThatThrownBy(() -> parser.parse(yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workflowType");
        }

        @Test
        @DisplayName("throws on missing states")
        void throwsOnMissingStates() {
            String yaml = """
                    workflowType: "test.wf"
                    version: 1
                    initialState: A
                    terminalStates: [B]
                    """;

            assertThatThrownBy(() -> parser.parse(yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("states");
        }

        @Test
        @DisplayName("throws on missing initialState")
        void throwsOnMissingInitialState() {
            String yaml = """
                    workflowType: "test.wf"
                    version: 1
                    states: [A, B]
                    terminalStates: [B]
                    """;

            assertThatThrownBy(() -> parser.parse(yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("initialState");
        }

        @Test
        @DisplayName("throws on malformed YAML")
        void throwsOnMalformedYaml() {
            String yaml = """
                    workflowType: "test.wf"
                    states: [A, B
                    invalid: {unclosed
                    """;

            assertThatThrownBy(() -> parser.parse(yaml))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("throws on step missing required id")
        void throwsOnStepMissingId() {
            String yaml = """
                    workflowType: "test.wf"
                    version: 1
                    states: [A, B]
                    initialState: A
                    terminalStates: [B]
                    steps:
                      - kind: task
                        from: A
                        to: B
                    """;

            assertThatThrownBy(() -> parser.parse(yaml))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }
    }

    // =====================================================================
    // Fork/join parsing
    // =====================================================================

    @Nested
    @DisplayName("Fork/join parsing")
    class ForkJoinTests {

        @Test
        @DisplayName("parses workflow with fork/join specs")
        void parsesForkJoinSpecs() {
            String yaml = """
                    workflowType: "parallel.wf"
                    version: 1
                    states: [INIT, FORKED, JOINED, DONE]
                    initialState: INIT
                    terminalStates: [DONE]
                    steps:
                      - id: forkStep
                        kind: fork
                        from: INIT
                        to: FORKED
                      - id: joinStep
                        kind: join
                        from: JOINED
                        to: DONE
                    forkJoinSpecs:
                      - forkStepId: forkStep
                        branchNames: [branchA, branchB]
                        joinStepId: joinStep
                        joinPolicy: ALL
                    """;

            WorkflowDefinition def = parser.parse(yaml);

            assertThat(def.forkJoinSpecs()).isNotNull();
            assertThat(def.forkJoinSpecs()).hasSize(1);
            ForkSpec spec = def.forkJoinSpecs().get(0);
            assertThat(spec.forkStepId()).isEqualTo("forkStep");
            assertThat(spec.branchNames()).containsExactly("branchA", "branchB");
            assertThat(spec.joinStepId()).isEqualTo("joinStep");
            assertThat(spec.joinPolicy()).isEqualTo("ALL");
        }
    }
}
