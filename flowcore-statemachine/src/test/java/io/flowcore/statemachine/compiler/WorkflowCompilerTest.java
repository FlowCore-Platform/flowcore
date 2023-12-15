package io.flowcore.statemachine.compiler;

import io.flowcore.api.dto.ActivityDef;
import io.flowcore.api.dto.RetryPolicyDef;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TimeoutPolicyDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.TriggerDef;
import io.flowcore.api.dto.WorkflowDefinition;
import io.flowcore.statemachine.validation.WorkflowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowCompilerTest {

    private WorkflowCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new WorkflowCompiler();
    }

    @Test
    @DisplayName("compiles valid workflow into CompiledWorkflow")
    void compilesValidWorkflow() {
        WorkflowDefinition def = new WorkflowDefinition(
                "demo.card.issuance", "1",
                Set.of("INIT", "KYC_PENDING", "KYC_APPROVED", "ACTIVE", "FAILED"),
                "INIT",
                Set.of("ACTIVE", "FAILED"),
                List.of(
                        new StepDef("kycCheck", "asyncActivity", "INIT", "KYC_PENDING",
                                new ActivityDef("KycAdapter", "verify"),
                                new RetryPolicyDef("exponential", 5, 200, 5000, 0.2),
                                new TimeoutPolicyDef(10000, "FAILED"),
                                null),
                        new StepDef("provisionCard", "asyncActivity", "KYC_APPROVED", "ACTIVE",
                                new ActivityDef("CardAdapter", "provision"),
                                null, null, null)
                ),
                List.of(
                        new TransitionDef("kycApproved", "KYC_PENDING", "KYC_APPROVED",
                                new TriggerDef("event", "KYC_RESULT"),
                                "$.kyc.status == \"APPROVED\""),
                        new TransitionDef("kycFailed", "KYC_PENDING", "FAILED",
                                new TriggerDef("event", "KYC_FAILED"),
                                "$.kyc.status == \"REJECTED\"")
                ),
                null
        );

        CompiledWorkflow compiled = compiler.compile(def);

        // -- Definition
        assertThat(compiled.getDefinition()).isSameAs(def);

        // -- Adjacency map
        assertThat(compiled.getAdjacencyMap()).containsEntry("INIT", Set.of("KYC_PENDING"));
        assertThat(compiled.getAdjacencyMap()).containsEntry("KYC_PENDING", Set.of("KYC_APPROVED", "FAILED"));
        assertThat(compiled.getAdjacencyMap()).containsEntry("KYC_APPROVED", Set.of("ACTIVE"));

        // -- Step index
        assertThat(compiled.getStepIndex()).hasSize(2);
        assertThat(compiled.getStepIndex().get("kycCheck").toState()).isEqualTo("KYC_PENDING");
        assertThat(compiled.getStepIndex().get("provisionCard").toState()).isEqualTo("ACTIVE");

        // -- Transitions by from state
        assertThat(compiled.getTransitionsByFromState()).containsKey("KYC_PENDING");
        assertThat(compiled.getTransitionsByFromState().get("KYC_PENDING")).hasSize(2);
        assertThat(compiled.getTransitionsByFromState().get("KYC_PENDING").get(0).id())
                .isEqualTo("kycApproved");

        // -- Reachable states
        assertThat(compiled.getReachableStates())
                .containsExactlyInAnyOrder("INIT", "KYC_PENDING", "KYC_APPROVED", "ACTIVE", "FAILED");
    }

    @Test
    @DisplayName("compiles minimal workflow")
    void compilesMinimalWorkflow() {
        WorkflowDefinition def = new WorkflowDefinition(
                "simple.wf", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        new ActivityDef("a", "op"), null, null, null)),
                List.of(), null
        );

        CompiledWorkflow compiled = compiler.compile(def);

        assertThat(compiled.getAdjacencyMap()).hasSize(1);
        assertThat(compiled.getAdjacencyMap()).containsEntry("INIT", Set.of("DONE"));
        assertThat(compiled.getStepIndex()).hasSize(1);
        assertThat(compiled.getReachableStates()).containsExactlyInAnyOrder("INIT", "DONE");
    }

    @Test
    @DisplayName("rejects invalid workflow during compilation")
    void rejectsInvalidWorkflow() {
        // PROCESSING is reachable from INIT, but has no outgoing transitions -> dead end.
        // DONE is reachable via transition and is terminal.
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "PROCESSING", "DONE"), "INIT", Set.of("DONE"),
                List.of(
                        new StepDef("s1", "task", "INIT", "PROCESSING",
                                new ActivityDef("a", "op"), null, null, null),
                        new StepDef("s2", "task", "INIT", "DONE",
                                new ActivityDef("a", "op"), null, null, null)
                ),
                List.of(), null
        );

        // PROCESSING has no outgoing transitions -> dead end
        assertThatThrownBy(() -> compiler.compile(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.DEAD_END_STATE);
    }

    @Test
    @DisplayName("compiles workflow with multiple transitions from same state")
    void compilesWithMultipleTransitionsFromSameState() {
        WorkflowDefinition def = new WorkflowDefinition(
                "multi.transition", "1",
                Set.of("INIT", "APPROVED", "REJECTED", "DONE"),
                "INIT",
                Set.of("DONE"),
                List.of(
                        new StepDef("s1", "task", "INIT", "APPROVED",
                                new ActivityDef("a", "op"), null, null, null),
                        new StepDef("s2", "task", "APPROVED", "DONE",
                                new ActivityDef("a", "op"), null, null, null)
                ),
                List.of(
                        new TransitionDef("t1", "INIT", "APPROVED",
                                new TriggerDef("event", "approved"), null),
                        new TransitionDef("t2", "INIT", "REJECTED",
                                new TriggerDef("event", "rejected"), null),
                        new TransitionDef("t3", "REJECTED", "DONE",
                                new TriggerDef("event", "closed"), null)
                ),
                null
        );

        CompiledWorkflow compiled = compiler.compile(def);

        // INIT -> APPROVED (via step), INIT -> APPROVED (via transition), INIT -> REJECTED (via transition)
        Set<String> fromInit = compiled.getAdjacencyMap().get("INIT");
        assertThat(fromInit).containsExactlyInAnyOrder("APPROVED", "REJECTED");

        List<TransitionDef> initTransitions = compiled.getTransitionsByFromState().get("INIT");
        assertThat(initTransitions).hasSize(2);

        assertThat(compiled.getReachableStates())
                .containsExactlyInAnyOrder("INIT", "APPROVED", "REJECTED", "DONE");
    }

    @Test
    @DisplayName("compiled workflow is immutable")
    void compiledWorkflowIsImmutable() {
        WorkflowDefinition def = new WorkflowDefinition(
                "immutable.test", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        new ActivityDef("a", "op"), null, null, null)),
                List.of(), null
        );

        CompiledWorkflow compiled = compiler.compile(def);

        assertThatThrownBy(() -> compiled.getAdjacencyMap().put("X", Set.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> compiled.getStepIndex().put("X", null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> compiled.getReachableStates().add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> compiled.getTransitionsByFromState().put("X", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
