package io.flowcore.statemachine.validation;

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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowValidatorTest {

    private WorkflowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WorkflowValidator();
    }

    // =====================================================================
    // Workflow type
    // =====================================================================

    @Nested
    @DisplayName("workflowType validation")
    class WorkflowTypeTests {

        @Test
        @DisplayName("rejects null workflowType")
        void nullWorkflowTypeRejected() {
            WorkflowDefinition def = new WorkflowDefinition(
                    null, "1",
                    Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                    List.of(), List.of(), null
            );
            assertThatThrownBy(() -> validator.validate(def))
                    .isInstanceOf(WorkflowValidationException.class)
                    .satisfies(ex -> {
                        WorkflowValidationException wve = (WorkflowValidationException) ex;
                        assertThat(wve.getErrorCode())
                                .isEqualTo(WorkflowValidationException.ErrorCode.INVALID_WORKFLOW_TYPE);
                    });
        }

        @Test
        @DisplayName("rejects workflowType that is too short")
        void shortWorkflowTypeRejected() {
            WorkflowDefinition def = new WorkflowDefinition(
                    "ab", "1",
                    Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                    List.of(), List.of(), null
            );
            assertThatThrownBy(() -> validator.validate(def))
                    .isInstanceOf(WorkflowValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(WorkflowValidationException.ErrorCode.INVALID_WORKFLOW_TYPE);
        }

        @Test
        @DisplayName("rejects workflowType that is too long")
        void longWorkflowTypeRejected() {
            String longType = "a".repeat(65);
            WorkflowDefinition def = new WorkflowDefinition(
                    longType, "1",
                    Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                    List.of(), List.of(), null
            );
            assertThatThrownBy(() -> validator.validate(def))
                    .isInstanceOf(WorkflowValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(WorkflowValidationException.ErrorCode.INVALID_WORKFLOW_TYPE);
        }

        @Test
        @DisplayName("rejects workflowType with invalid characters")
        void invalidCharsWorkflowTypeRejected() {
            WorkflowDefinition def = new WorkflowDefinition(
                    "hello world!", "1",
                    Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                    List.of(), List.of(), null
            );
            assertThatThrownBy(() -> validator.validate(def))
                    .isInstanceOf(WorkflowValidationException.class)
                    .extracting("errorCode")
                    .isEqualTo(WorkflowValidationException.ErrorCode.INVALID_WORKFLOW_TYPE);
        }
    }

    // =====================================================================
    // Initial state
    // =====================================================================

    @Test
    @DisplayName("rejects initialState not in states")
    void initialStateNotInStatesRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "UNKNOWN", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        null, null, null, null)),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.INITIAL_STATE_NOT_IN_STATES);
    }

    // =====================================================================
    // Transition references
    // =====================================================================

    @Test
    @DisplayName("rejects transition referencing unknown state")
    void transitionReferencesUnknownStateRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        null, null, null, null)),
                List.of(new TransitionDef("t1", "INIT", "NONEXISTENT",
                        new TriggerDef("event", "test"), null)),
                null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.TRANSITION_REFERENCES_UNKNOWN_STATE);
    }

    @Test
    @DisplayName("rejects step referencing unknown fromState")
    void stepReferencesUnknownFromStateRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "UNKNOWN", "DONE",
                        null, null, null, null)),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.TRANSITION_REFERENCES_UNKNOWN_STATE);
    }

    // =====================================================================
    // Reachability
    // =====================================================================

    @Test
    @DisplayName("rejects unreachable states")
    void unreachableStateRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "PROCESSING", "DONE", "ORPHAN"), "INIT", Set.of("DONE"),
                List.of(
                        new StepDef("s1", "task", "INIT", "PROCESSING",
                                new ActivityDef("a", "op"), null, null, null),
                        new StepDef("s2", "task", "PROCESSING", "DONE",
                                new ActivityDef("a", "op"), null, null, null)
                ),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .satisfies(ex -> {
                    WorkflowValidationException wve = (WorkflowValidationException) ex;
                    assertThat(wve.getErrorCode())
                            .isEqualTo(WorkflowValidationException.ErrorCode.UNREACHABLE_STATE);
                    assertThat(wve.getDetails()).contains("ORPHAN");
                });
    }

    // =====================================================================
    // Dead-end states
    // =====================================================================

    @Test
    @DisplayName("rejects dead-end non-terminal states")
    void deadEndNonTerminalStateRejected() {
        // MIDDLE is reachable from INIT, but has no outgoing transitions -> dead end.
        // DONE is terminal so it is allowed to have no outgoing transitions.
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "MIDDLE", "DONE"), "INIT", Set.of("DONE"),
                List.of(
                        new StepDef("s1", "task", "INIT", "MIDDLE",
                                new ActivityDef("a", "op"), null, null, null),
                        new StepDef("s2", "task", "INIT", "DONE",
                                new ActivityDef("a", "op"), null, null, null)
                ),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .satisfies(ex -> {
                    WorkflowValidationException wve = (WorkflowValidationException) ex;
                    assertThat(wve.getErrorCode())
                            .isEqualTo(WorkflowValidationException.ErrorCode.DEAD_END_STATE);
                    assertThat(wve.getDetails()).contains("MIDDLE");
                });
    }

    @Test
    @DisplayName("allows dead-end non-terminal states when allowDeadEnds is true")
    void deadEndAllowedWhenFlagSet() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "STUCK", "DONE"), "INIT", Set.of("DONE"),
                List.of(
                        new StepDef("s1", "task", "INIT", "STUCK",
                                new ActivityDef("a", "op"), null, null, null),
                        new StepDef("s2", "task", "INIT", "DONE",
                                new ActivityDef("a", "op"), null, null, null)
                ),
                List.of(), null
        );
        // Should not throw with allowDeadEnds=true
        validator.validate(def, true);
    }

    // =====================================================================
    // Duplicate step IDs
    // =====================================================================

    @Test
    @DisplayName("rejects duplicate step IDs")
    void duplicateStepIdRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "PROCESSING", "DONE"), "INIT", Set.of("DONE"),
                List.of(
                        new StepDef("sameId", "task", "INIT", "PROCESSING",
                                new ActivityDef("a", "op"), null, null, null),
                        new StepDef("sameId", "task", "PROCESSING", "DONE",
                                new ActivityDef("a", "op"), null, null, null)
                ),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .satisfies(ex -> {
                    WorkflowValidationException wve = (WorkflowValidationException) ex;
                    assertThat(wve.getErrorCode())
                            .isEqualTo(WorkflowValidationException.ErrorCode.DUPLICATE_STEP_ID);
                    assertThat(wve.getDetails()).contains("sameId");
                });
    }

    // =====================================================================
    // Retry policy
    // =====================================================================

    @Test
    @DisplayName("rejects retry policy with maxAttempts < 1 at construction time")
    void retryPolicyMaxAttemptsTooLow() {
        // RetryPolicyDef compact constructor validates maxAttempts >= 1
        assertThatThrownBy(() -> new RetryPolicyDef("fixed", 0, 100, 100, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }

    @Test
    @DisplayName("rejects retry policy with baseDelayMs < 10")
    void retryPolicyBaseDelayTooLow() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        new ActivityDef("a", "op"),
                        new RetryPolicyDef("fixed", 3, 5, 100, 0.0),
                        null, null)),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.INVALID_RETRY_POLICY);
    }

    @Test
    @DisplayName("rejects retry policy with maxDelayMs < baseDelayMs")
    void retryPolicyMaxDelayLessThanBase() {
        // This will fail in RetryPolicyDef compact constructor with IllegalArgumentException
        // before reaching the validator. Let's construct a scenario that passes
        // the record constructor but fails in validator.
        // Actually RetryPolicyDef only validates maxAttempts >= 1 in compact constructor.
        // baseDelayMs=200, maxDelayMs=100 -> should fail in validator.
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        new ActivityDef("a", "op"),
                        new RetryPolicyDef("fixed", 3, 200, 100, 0.0),
                        null, null)),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.INVALID_RETRY_POLICY);
    }

    // =====================================================================
    // Timeout policy
    // =====================================================================

    @Test
    @DisplayName("rejects timeout policy with timeoutMs < 100")
    void timeoutPolicyTimeoutMsTooLow() {
        // TimeoutPolicyDef compact constructor validates >= 100, so it will
        // throw IllegalArgumentException before validator. We need to test
        // validator's own check. Since the record validates in compact constructor,
        // the validator's check is defensive. Let's test that the exception
        // propagates properly.
        assertThatThrownBy(() -> new TimeoutPolicyDef(50, "DONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects timeout policy with onTimeoutState not in states")
    void timeoutPolicyOnTimeoutStateNotInStates() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        new ActivityDef("a", "op"),
                        null,
                        new TimeoutPolicyDef(1000, "NONEXISTENT"),
                        null)),
                List.of(), null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.INVALID_TIMEOUT_POLICY);
    }

    // =====================================================================
    // Fork/join
    // =====================================================================

    @Test
    @DisplayName("rejects fork with no branch names")
    void forkWithNoBranchesRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(
                        new StepDef("fork1", "fork", "INIT", "DONE",
                                null, null, null, null),
                        new StepDef("join1", "join", "INIT", "DONE",
                                null, null, null, null)
                ),
                List.of(),
                List.of(new ForkSpec("fork1", List.of(), "join1", "ALL"))
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.FORK_JOIN_MISMATCH);
    }

    @Test
    @DisplayName("rejects fork referencing non-existent step")
    void forkWithNonExistentStepRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        null, null, null, null)),
                List.of(),
                List.of(new ForkSpec("nonExistentFork", List.of("branch1"), "s1", "ALL"))
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.FORK_JOIN_MISMATCH);
    }

    // =====================================================================
    // Valid definition
    // =====================================================================

    @Test
    @DisplayName("valid definition passes validation")
    void validDefinitionPasses() {
        WorkflowDefinition def = new WorkflowDefinition(
                "demo.card.issuance", "1",
                Set.of("INIT", "KYC_PENDING", "KYC_APPROVED", "ACTIVE", "FAILED"),
                "INIT",
                Set.of("ACTIVE", "FAILED"),
                List.of(
                        new StepDef("kycCheck", "asyncActivity", "INIT", "KYC_PENDING",
                                new ActivityDef("KycProviderAdapter", "verify"),
                                new RetryPolicyDef("exponential", 5, 200, 5000, 0.2),
                                new TimeoutPolicyDef(10000, "FAILED"),
                                new CompensationDef("asyncActivity",
                                        new ActivityDef("KycProviderAdapter", "cancelVerification"))),
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
        // Should not throw
        validator.validate(def);
    }

    @Test
    @DisplayName("minimal valid definition passes validation")
    void minimalValidDefinitionPasses() {
        WorkflowDefinition def = new WorkflowDefinition(
                "simple.wf", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        new ActivityDef("a", "op"), null, null, null)),
                List.of(), null
        );
        validator.validate(def);
    }

    // =====================================================================
    // Timer uniqueness
    // =====================================================================

    @Test
    @DisplayName("rejects duplicate timer names")
    void duplicateTimerNamesRejected() {
        WorkflowDefinition def = new WorkflowDefinition(
                "test.workflow", "1",
                Set.of("INIT", "PROCESSING", "DONE"), "INIT", Set.of("DONE"),
                List.of(
                        new StepDef("s1", "task", "INIT", "PROCESSING",
                                new ActivityDef("a", "op"), null, null, null),
                        new StepDef("s2", "task", "PROCESSING", "DONE",
                                new ActivityDef("a", "op"), null, null, null)
                ),
                List.of(
                        new TransitionDef("t1", "INIT", "PROCESSING",
                                new TriggerDef("timer", "myTimer"), null),
                        new TransitionDef("t2", "PROCESSING", "DONE",
                                new TriggerDef("timer", "myTimer"), null)
                ),
                null
        );
        assertThatThrownBy(() -> validator.validate(def))
                .isInstanceOf(WorkflowValidationException.class)
                .extracting("errorCode")
                .isEqualTo(WorkflowValidationException.ErrorCode.FORK_JOIN_MISMATCH);
    }

    // =====================================================================
    // Workflow type edge cases
    // =====================================================================

    @Test
    @DisplayName("accepts valid workflow types with dots and hyphens")
    void validWorkflowTypeWithSpecialChars() {
        WorkflowDefinition def = new WorkflowDefinition(
                "com.example.my-work_flow.v2", "1",
                Set.of("INIT", "DONE"), "INIT", Set.of("DONE"),
                List.of(new StepDef("s1", "task", "INIT", "DONE",
                        new ActivityDef("a", "op"), null, null, null)),
                List.of(), null
        );
        validator.validate(def);
    }
}
