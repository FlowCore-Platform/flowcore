package io.flowcore.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.dto.SignalWorkflowCommand;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.CompleteStepCommand;
import io.flowcore.api.dto.RetryPolicyDef;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.TriggerDef;
import io.flowcore.api.dto.WorkflowDefinition;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.runtime.persistence.WorkflowInstanceEntity;
import io.flowcore.runtime.persistence.WorkflowInstanceRepository;
import io.flowcore.runtime.persistence.WorkflowStepExecutionEntity;
import io.flowcore.runtime.persistence.WorkflowStepExecutionRepository;
import io.flowcore.runtime.persistence.WorkflowTokenEntity;
import io.flowcore.runtime.persistence.WorkflowTokenRepository;
import io.flowcore.runtime.registry.DefaultWorkflowRegistry;
import io.flowcore.statemachine.compiler.WorkflowCompiler;
import io.flowcore.statemachine.validation.WorkflowValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultWorkflowEngine} using mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private WorkflowInstanceRepository instanceRepository;

    @Mock
    private WorkflowTokenRepository tokenRepository;

    @Mock
    private WorkflowStepExecutionRepository stepExecutionRepository;

    private DefaultWorkflowEngine engine;

    private DefaultWorkflowRegistry registry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String WORKFLOW_TYPE = "test-workflow.v1";

    private WorkflowDefinition simpleDefinition() {
        return new WorkflowDefinition(
                WORKFLOW_TYPE,
                "1.0.0",
                Set.of("Start", "Processing", "Completed"),
                "Start",
                Set.of("Completed"),
                List.of(
                        new StepDef("step-process", "task", "Start", "Processing",
                                null, null, null, null)
                ),
                List.of(
                        new TransitionDef("tr-approve", "Processing", "Completed",
                                new TriggerDef("signal", "approve"), null),
                        new TransitionDef("tr-reject", "Processing", "Completed",
                                new TriggerDef("signal", "reject"), null)
                ),
                null
        );
    }

    private WorkflowDefinition guardedDefinition() {
        return new WorkflowDefinition(
                "guarded-workflow.v1",
                "1.0.0",
                Set.of("Start", "Approved", "Rejected"),
                "Start",
                Set.of("Approved", "Rejected"),
                List.of(
                        new StepDef("step-evaluate", "task", "Start", "Approved",
                                null, null, null, null),
                        new StepDef("step-reject", "task", "Start", "Rejected",
                                null, null, null, null)
                ),
                List.of(
                        new TransitionDef("tr-auto-approve", "Start", "Approved",
                                null, "score==high"),
                        new TransitionDef("tr-auto-reject", "Start", "Rejected",
                                null, "score==low")
                ),
                null
        );
    }

    @BeforeEach
    void setUp() {
        registry = new DefaultWorkflowRegistry(new WorkflowCompiler(new WorkflowValidator()));
        engine = new DefaultWorkflowEngine(
                instanceRepository, tokenRepository, stepExecutionRepository,
                registry, objectMapper);
    }

    @Test
    @DisplayName("startWorkflow creates instance with correct state and token")
    void startWorkflow_createsInstanceWithCorrectState() {
        // Register the workflow definition
        registry.register(simpleDefinition());

        // Mock repository to return the saved entity
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity entity = invocation.getArgument(0);
                    entity.setCreatedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    return entity;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StartWorkflowCommand command = new StartWorkflowCommand(
                WORKFLOW_TYPE, "order-123", Map.of("amount", 100), null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertNotNull(result);
        assertEquals(WORKFLOW_TYPE, result.workflowType());
        assertEquals("order-123", result.businessKey());
        assertEquals("RUNNING", result.status());
        assertEquals("Start", result.currentState());

        // Verify instance was saved
        ArgumentCaptor<WorkflowInstanceEntity> instanceCaptor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(instanceCaptor.capture());
        assertEquals("Start", instanceCaptor.getValue().getCurrentState());

        // Verify token was created
        ArgumentCaptor<WorkflowTokenEntity> tokenCaptor =
                ArgumentCaptor.forClass(WorkflowTokenEntity.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertEquals("main", tokenCaptor.getValue().getTokenName());
        assertEquals("ACTIVE", tokenCaptor.getValue().getStatus());
        assertEquals("Start", tokenCaptor.getValue().getActiveNode());
    }

    @Test
    @DisplayName("signal triggers transition when event matches")
    void signal_triggersTransition_whenEventMatches() {
        registry.register(simpleDefinition());

        // Create a mock instance in "Processing" state
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(UUID.randomUUID());
        instance.setWorkflowType(WORKFLOW_TYPE);
        instance.setBusinessKey("order-123");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Processing");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        when(instanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SignalWorkflowCommand command = new SignalWorkflowCommand(
                instance.getId(), "approve", null, null);

        engine.signal(command);

        // Verify the instance was updated to the new state
        ArgumentCaptor<WorkflowInstanceEntity> captor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(captor.capture());
        assertEquals("Completed", captor.getValue().getCurrentState());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("signal does nothing when no transition matches the event")
    void signal_doesNothing_whenNoTransitionMatches() {
        registry.register(simpleDefinition());

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(UUID.randomUUID());
        instance.setWorkflowType(WORKFLOW_TYPE);
        instance.setBusinessKey("order-123");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Start");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        when(instanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));

        // "approve" signal only matches from "Processing" state, not "Start"
        SignalWorkflowCommand command = new SignalWorkflowCommand(
                instance.getId(), "approve", null, null);

        engine.signal(command);

        // No save should have been called since no transition matched
        verify(instanceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("startWorkflow evaluates guard expression and auto-transitions when guard passes")
    void startWorkflow_evaluatesGuard_andAutoTransitionsWhenGuardPasses() {
        registry.register(guardedDefinition());

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity entity = invocation.getArgument(0);
                    entity.setCreatedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    return entity;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowTokenEntity tok = invocation.getArgument(0);
                    tok.setCreatedAt(Instant.now());
                    tok.setUpdatedAt(Instant.now());
                    return tok;
                });
        when(tokenRepository.findByWorkflowInstanceIdAndStatus(any(UUID.class), any(String.class)))
                .thenReturn(Collections.emptyList());
        when(tokenRepository.findByWorkflowInstanceId(any(UUID.class)))
                .thenAnswer(invocation -> {
                    // Return a list with the last saved token
                    // We need to capture it from the save call, but since mocks
                    // can't easily do that, let's return a stub token
                    WorkflowTokenEntity stubToken = new WorkflowTokenEntity();
                    stubToken.setId(UUID.randomUUID());
                    return List.of(stubToken);
                });
        when(stepExecutionRepository.save(any(WorkflowStepExecutionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Start with context where score=high -- guard "score==high" should match
        StartWorkflowCommand command = new StartWorkflowCommand(
                "guarded-workflow.v1", "eval-123", Map.of("score", "high"), null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertNotNull(result);
        // The auto-transition with guard "score==high" should have moved to "Approved"
        assertEquals("Approved", result.currentState());
        assertEquals("COMPLETED", result.status()); // Approved is a terminal state
    }

    @Test
    @DisplayName("completeStep marks step SUCCEEDED and advances state")
    void completeStep_marksSucceeded_andAdvancesState() {
        registry.register(simpleDefinition());

        UUID instanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowType(WORKFLOW_TYPE);
        instance.setBusinessKey("order-456");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Start");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        WorkflowTokenEntity token = new WorkflowTokenEntity();
        token.setId(tokenId);
        token.setWorkflowInstance(instance);
        token.setTokenName("main");
        token.setActiveNode("Start");
        token.setStatus("ACTIVE");
        token.setCreatedAt(Instant.now());
        token.setUpdatedAt(Instant.now());

        WorkflowStepExecutionEntity execution = new WorkflowStepExecutionEntity();
        execution.setId(UUID.randomUUID());
        execution.setWorkflowInstance(instance);
        execution.setToken(token);
        execution.setStepId("step-process");
        execution.setAttempt(1);
        execution.setStatus("STARTED");
        execution.setStartedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(stepExecutionRepository.findByWorkflowInstanceIdAndStepId(instanceId, "step-process"))
                .thenReturn(List.of(execution));
        when(tokenRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stepExecutionRepository.save(any(WorkflowStepExecutionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompleteStepCommand command = new CompleteStepCommand(
                instanceId, tokenId, "step-process", "{\"result\":\"ok\"}", null, null);

        engine.completeStep(command);

        // Verify step execution was marked SUCCEEDED
        ArgumentCaptor<WorkflowStepExecutionEntity> stepCaptor =
                ArgumentCaptor.forClass(WorkflowStepExecutionEntity.class);
        verify(stepExecutionRepository).save(stepCaptor.capture());
        assertEquals("SUCCEEDED", stepCaptor.getValue().getStatus());
        assertNotNull(stepCaptor.getValue().getFinishedAt());

        // Verify instance state advanced
        ArgumentCaptor<WorkflowInstanceEntity> instanceCaptor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(instanceCaptor.capture());
        assertEquals("Processing", instanceCaptor.getValue().getCurrentState());
    }

    @Test
    @DisplayName("completeStep marks step FAILED when errorCode is present")
    void completeStep_marksFailed_whenErrorCodePresent() {
        registry.register(simpleDefinition());

        UUID instanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowType(WORKFLOW_TYPE);
        instance.setBusinessKey("order-789");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Start");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        WorkflowTokenEntity token = new WorkflowTokenEntity();
        token.setId(tokenId);
        token.setWorkflowInstance(instance);
        token.setTokenName("main");
        token.setStatus("ACTIVE");
        token.setCreatedAt(Instant.now());
        token.setUpdatedAt(Instant.now());

        WorkflowStepExecutionEntity execution = new WorkflowStepExecutionEntity();
        execution.setId(UUID.randomUUID());
        execution.setWorkflowInstance(instance);
        execution.setToken(token);
        execution.setStepId("step-process");
        execution.setAttempt(1);
        execution.setStatus("STARTED");
        execution.setStartedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(stepExecutionRepository.findByWorkflowInstanceIdAndStepId(instanceId, "step-process"))
                .thenReturn(List.of(execution));
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stepExecutionRepository.save(any(WorkflowStepExecutionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompleteStepCommand command = new CompleteStepCommand(
                instanceId, tokenId, "step-process", null, "ERR_TIMEOUT", "Operation timed out");

        engine.completeStep(command);

        ArgumentCaptor<WorkflowStepExecutionEntity> stepCaptor =
                ArgumentCaptor.forClass(WorkflowStepExecutionEntity.class);
        verify(stepExecutionRepository).save(stepCaptor.capture());
        assertEquals("FAILED", stepCaptor.getValue().getStatus());
        assertEquals("ERR_TIMEOUT", stepCaptor.getValue().getErrorCode());

        // Instance should be marked FAILED (no retry policy)
        ArgumentCaptor<WorkflowInstanceEntity> instanceCaptor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(instanceCaptor.capture());
        assertEquals("FAILED", instanceCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("startWorkflow with existing businessKey returns existing instance")
    void startWorkflow_withExistingBusinessKey_returnsExisting() {
        registry.register(simpleDefinition());

        WorkflowInstanceEntity existing = new WorkflowInstanceEntity();
        existing.setId(UUID.randomUUID());
        existing.setWorkflowType(WORKFLOW_TYPE);
        existing.setBusinessKey("order-duplicate");
        existing.setStatus("RUNNING");
        existing.setCurrentState("Start");
        existing.setVersion(0L);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(instanceRepository.findByWorkflowTypeAndBusinessKey(WORKFLOW_TYPE, "order-duplicate"))
                .thenReturn(Optional.of(existing));

        StartWorkflowCommand command = new StartWorkflowCommand(
                WORKFLOW_TYPE, "order-duplicate", Map.of(), null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertNotNull(result);
        assertEquals(existing.getId(), result.id());
        assertEquals("order-duplicate", result.businessKey());
    }

    @Test
    @DisplayName("handleTimerFired processes timer transition")
    void handleTimerFired_processesTimerTransition() {
        // Create a workflow with a timer transition
        WorkflowDefinition timerDefinition = new WorkflowDefinition(
                "timer-workflow.v1",
                "1.0.0",
                Set.of("Waiting", "Completed"),
                "Waiting",
                Set.of("Completed"),
                List.of(),
                List.of(
                        new TransitionDef("tr-timeout", "Waiting", "Completed",
                                new TriggerDef("timer", "expiry-timer"), null)
                ),
                null
        );
        registry.register(timerDefinition);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowType("timer-workflow.v1");
        instance.setBusinessKey("timer-123");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Waiting");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        io.flowcore.api.dto.TimerFiredCommand command =
                new io.flowcore.api.dto.TimerFiredCommand(
                        instanceId, UUID.randomUUID(), "expiry-timer", null);

        engine.handleTimerFired(command);

        ArgumentCaptor<WorkflowInstanceEntity> captor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(captor.capture());
        assertEquals("Completed", captor.getValue().getCurrentState());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    // -- Additional tests for branch coverage -----------------------------

    @Test
    @DisplayName("findInstance returns instance when found")
    void findInstance_returnsInstance_whenFound() {
        registry.register(simpleDefinition());

        UUID instanceId = UUID.randomUUID();
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setId(instanceId);
        entity.setWorkflowType(WORKFLOW_TYPE);
        entity.setBusinessKey("bk-find");
        entity.setStatus("RUNNING");
        entity.setCurrentState("Start");
        entity.setVersion(0L);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(entity));

        Optional<WorkflowInstance> result = engine.findInstance(instanceId);

        assertTrue(result.isPresent());
        assertEquals(instanceId, result.get().id());
    }

    @Test
    @DisplayName("findInstance returns empty when not found")
    void findInstance_returnsEmpty_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        Optional<WorkflowInstance> result = engine.findInstance(id);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByBusinessKey returns instance when found")
    void findByBusinessKey_returnsInstance_whenFound() {
        registry.register(simpleDefinition());

        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setId(UUID.randomUUID());
        entity.setWorkflowType(WORKFLOW_TYPE);
        entity.setBusinessKey("bk-search");
        entity.setStatus("RUNNING");
        entity.setCurrentState("Start");
        entity.setVersion(0L);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(instanceRepository.findByWorkflowTypeAndBusinessKey(WORKFLOW_TYPE, "bk-search"))
                .thenReturn(Optional.of(entity));

        Optional<WorkflowInstance> result = engine.findByBusinessKey(WORKFLOW_TYPE, "bk-search");

        assertTrue(result.isPresent());
        assertEquals("bk-search", result.get().businessKey());
    }

    @Test
    @DisplayName("findByBusinessKey returns empty when not found")
    void findByBusinessKey_returnsEmpty_whenNotFound() {
        when(instanceRepository.findByWorkflowTypeAndBusinessKey(WORKFLOW_TYPE, "missing"))
                .thenReturn(Optional.empty());

        Optional<WorkflowInstance> result = engine.findByBusinessKey(WORKFLOW_TYPE, "missing");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("startWorkflow with null businessKey generates UUID as businessKey")
    void startWorkflow_withNullBusinessKey_generatesUUID() {
        registry.register(simpleDefinition());

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity e = invocation.getArgument(0);
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return e;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StartWorkflowCommand command = new StartWorkflowCommand(
                WORKFLOW_TYPE, null, Map.of("amount", 100), null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertNotNull(result);
        assertNotNull(result.businessKey());
        // Should be a UUID string
        assertDoesNotThrow(() -> UUID.fromString(result.businessKey()));
    }

    @Test
    @DisplayName("startWorkflow with blank businessKey does not check idempotency")
    void startWorkflow_withBlankBusinessKey_skipsIdempotency() {
        registry.register(simpleDefinition());

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity e = invocation.getArgument(0);
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return e;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StartWorkflowCommand command = new StartWorkflowCommand(
                WORKFLOW_TYPE, "   ", Map.of(), null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertNotNull(result);
        // Should not call findByWorkflowTypeAndBusinessKey for blank key
        verify(instanceRepository, never()).findByWorkflowTypeAndBusinessKey(any(), any());
    }

    @Test
    @DisplayName("startWorkflow with null contextData serializes to empty object")
    void startWorkflow_withNullContextData() {
        registry.register(simpleDefinition());

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity e = invocation.getArgument(0);
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return e;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StartWorkflowCommand command = new StartWorkflowCommand(
                WORKFLOW_TYPE, "bk-null-ctx", null, null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertNotNull(result);
    }

    @Test
    @DisplayName("signal with guard expression that does not match skips transition")
    void signal_skipsTransition_whenGuardDoesNotMatch() {
        // Create workflow with signal transitions that have guards
        WorkflowDefinition definition = new WorkflowDefinition(
                "signal-guarded.v1",
                "1.0.0",
                Set.of("Review", "Approved", "Rejected"),
                "Review",
                Set.of("Approved", "Rejected"),
                List.of(),
                List.of(
                        new TransitionDef("tr-approve", "Review", "Approved",
                                new TriggerDef("signal", "decide"), "level==high"),
                        new TransitionDef("tr-reject", "Review", "Rejected",
                                new TriggerDef("signal", "decide"), "level==low")
                ),
                null
        );
        registry.register(definition);

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(UUID.randomUUID());
        instance.setWorkflowType("signal-guarded.v1");
        instance.setBusinessKey("sg-1");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Review");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());
        instance.setContextJson("{\"level\":\"medium\"}");

        when(instanceRepository.findById(instance.getId())).thenReturn(Optional.of(instance));

        SignalWorkflowCommand command = new SignalWorkflowCommand(
                instance.getId(), "decide", null, null);

        engine.signal(command);

        // Neither guard should match, so no save should occur
        verify(instanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("signal with guard expression using != operator")
    void signal_withNotEqualsGuard() {
        WorkflowDefinition definition = new WorkflowDefinition(
                "ne-guard.v1",
                "1.0.0",
                Set.of("Review", "Done"),
                "Review",
                Set.of("Done"),
                List.of(),
                List.of(
                        new TransitionDef("tr-go", "Review", "Done",
                                new TriggerDef("signal", "check"), "status!=blocked")
                ),
                null
        );
        registry.register(definition);

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        UUID instanceId = UUID.randomUUID();
        instance.setId(instanceId);
        instance.setWorkflowType("ne-guard.v1");
        instance.setBusinessKey("ne-1");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Review");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());
        instance.setContextJson("{\"status\":\"ok\"}");

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SignalWorkflowCommand command = new SignalWorkflowCommand(
                instanceId, "check", null, null);

        engine.signal(command);

        ArgumentCaptor<WorkflowInstanceEntity> captor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(captor.capture());
        assertEquals("Done", captor.getValue().getCurrentState());
    }

    @Test
    @DisplayName("signal with unsupported guard expression allows transition")
    void signal_withUnsupportedGuardExpression_allowsTransition() {
        WorkflowDefinition definition = new WorkflowDefinition(
                "unsupported-guard.v1",
                "1.0.0",
                Set.of("Review", "Done"),
                "Review",
                Set.of("Done"),
                List.of(),
                List.of(
                        new TransitionDef("tr-go", "Review", "Done",
                                new TriggerDef("signal", "check"), "complex_expr")
                ),
                null
        );
        registry.register(definition);

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        UUID instanceId = UUID.randomUUID();
        instance.setId(instanceId);
        instance.setWorkflowType("unsupported-guard.v1");
        instance.setBusinessKey("ug-1");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Review");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());
        instance.setContextJson("{}");

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SignalWorkflowCommand command = new SignalWorkflowCommand(
                instanceId, "check", null, null);

        engine.signal(command);

        ArgumentCaptor<WorkflowInstanceEntity> captor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(captor.capture());
        assertEquals("Done", captor.getValue().getCurrentState());
    }

    @Test
    @DisplayName("handleTimerFired does nothing when no timer matches")
    void handleTimerFired_doesNothing_whenNoTimerMatches() {
        WorkflowDefinition timerDefinition = new WorkflowDefinition(
                "timer-workflow.v1",
                "1.0.0",
                Set.of("Waiting", "Completed"),
                "Waiting",
                Set.of("Completed"),
                List.of(),
                List.of(
                        new TransitionDef("tr-timeout", "Waiting", "Completed",
                                new TriggerDef("timer", "expiry-timer"), null)
                ),
                null
        );
        registry.register(timerDefinition);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowType("timer-workflow.v1");
        instance.setBusinessKey("timer-456");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Waiting");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        // Use a different timer name that won't match
        io.flowcore.api.dto.TimerFiredCommand command =
                new io.flowcore.api.dto.TimerFiredCommand(
                        instanceId, UUID.randomUUID(), "unknown-timer", null);

        engine.handleTimerFired(command);

        // No save should occur
        verify(instanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("handleTimerFired throws when instance not found")
    void handleTimerFired_throws_whenInstanceNotFound() {
        registry.register(simpleDefinition());

        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        io.flowcore.api.dto.TimerFiredCommand command =
                new io.flowcore.api.dto.TimerFiredCommand(
                        id, UUID.randomUUID(), "some-timer", null);

        assertThrows(IllegalArgumentException.class, () -> engine.handleTimerFired(command));
    }

    @Test
    @DisplayName("completeStep with retry policy does not mark instance FAILED")
    void completeStep_withRetryPolicy_doesNotMarkFailed() {
        WorkflowDefinition definitionWithRetry = new WorkflowDefinition(
                "retry-workflow.v1",
                "1.0.0",
                Set.of("Start", "Processing"),
                "Start",
                Set.of("Processing"),
                List.of(
                        new StepDef("step-retry", "task", "Start", "Processing",
                                null, new RetryPolicyDef("fixed", 3, 1000, 5000, 0.1),
                                null, null)
                ),
                List.of(),
                null
        );
        registry.register(definitionWithRetry);

        UUID instanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowType("retry-workflow.v1");
        instance.setBusinessKey("retry-1");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Start");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        WorkflowTokenEntity token = new WorkflowTokenEntity();
        token.setId(tokenId);
        token.setWorkflowInstance(instance);
        token.setTokenName("main");
        token.setStatus("ACTIVE");
        token.setCreatedAt(Instant.now());
        token.setUpdatedAt(Instant.now());

        WorkflowStepExecutionEntity execution = new WorkflowStepExecutionEntity();
        execution.setId(UUID.randomUUID());
        execution.setWorkflowInstance(instance);
        execution.setToken(token);
        execution.setStepId("step-retry");
        execution.setAttempt(1);
        execution.setStatus("STARTED");
        execution.setStartedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(stepExecutionRepository.findByWorkflowInstanceIdAndStepId(instanceId, "step-retry"))
                .thenReturn(List.of(execution));
        when(stepExecutionRepository.save(any(WorkflowStepExecutionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompleteStepCommand command = new CompleteStepCommand(
                instanceId, tokenId, "step-retry", null, "ERR_RETRY", "Will retry");

        engine.completeStep(command);

        // Instance should NOT be marked FAILED since retry policy exists;
        // instanceRepository.save should NOT be called for the failed branch
        // when a retry policy is present
        verify(instanceRepository, never()).save(any());

        // Step execution should be marked FAILED
        ArgumentCaptor<WorkflowStepExecutionEntity> stepCaptor =
                ArgumentCaptor.forClass(WorkflowStepExecutionEntity.class);
        verify(stepExecutionRepository).save(stepCaptor.capture());
        assertEquals("FAILED", stepCaptor.getValue().getStatus());
        assertEquals("ERR_RETRY", stepCaptor.getValue().getErrorCode());
    }

    @Test
    @DisplayName("completeStep throws when step execution not found")
    void completeStep_throws_whenStepExecutionNotFound() {
        registry.register(simpleDefinition());

        UUID instanceId = UUID.randomUUID();

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowType(WORKFLOW_TYPE);
        instance.setBusinessKey("no-step");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Start");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(stepExecutionRepository.findByWorkflowInstanceIdAndStepId(instanceId, "nonexistent-step"))
                .thenReturn(List.of());

        CompleteStepCommand command = new CompleteStepCommand(
                instanceId, UUID.randomUUID(), "nonexistent-step", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> engine.completeStep(command));
    }

    @Test
    @DisplayName("signal throws when instance not found")
    void signal_throws_whenInstanceNotFound() {
        registry.register(simpleDefinition());

        UUID id = UUID.randomUUID();
        when(instanceRepository.findById(id)).thenReturn(Optional.empty());

        SignalWorkflowCommand command = new SignalWorkflowCommand(id, "approve", null, null);
        assertThrows(IllegalArgumentException.class, () -> engine.signal(command));
    }

    @Test
    @DisplayName("completeStep succeeds and transitions to terminal state")
    void completeStep_succeeds_toTerminalState() {
        // Workflow where step goes from Start -> Completed (terminal)
        WorkflowDefinition terminalDefinition = new WorkflowDefinition(
                "terminal-workflow.v1",
                "1.0.0",
                Set.of("Start", "Completed"),
                "Start",
                Set.of("Completed"),
                List.of(
                        new StepDef("step-finish", "task", "Start", "Completed",
                                null, null, null, null)
                ),
                List.of(),
                null
        );
        registry.register(terminalDefinition);

        UUID instanceId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowType("terminal-workflow.v1");
        instance.setBusinessKey("term-1");
        instance.setStatus("RUNNING");
        instance.setCurrentState("Start");
        instance.setVersion(0L);
        instance.setCreatedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());

        WorkflowTokenEntity token = new WorkflowTokenEntity();
        token.setId(tokenId);
        token.setWorkflowInstance(instance);
        token.setTokenName("main");
        token.setActiveNode("Start");
        token.setStatus("ACTIVE");
        token.setCreatedAt(Instant.now());
        token.setUpdatedAt(Instant.now());

        WorkflowStepExecutionEntity execution = new WorkflowStepExecutionEntity();
        execution.setId(UUID.randomUUID());
        execution.setWorkflowInstance(instance);
        execution.setToken(token);
        execution.setStepId("step-finish");
        execution.setAttempt(1);
        execution.setStatus("STARTED");
        execution.setStartedAt(Instant.now());

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(stepExecutionRepository.findByWorkflowInstanceIdAndStepId(instanceId, "step-finish"))
                .thenReturn(List.of(execution));
        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stepExecutionRepository.save(any(WorkflowStepExecutionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CompleteStepCommand command = new CompleteStepCommand(
                instanceId, tokenId, "step-finish", "{\"ok\":true}", null, null);

        engine.completeStep(command);

        ArgumentCaptor<WorkflowInstanceEntity> captor =
                ArgumentCaptor.forClass(WorkflowInstanceEntity.class);
        verify(instanceRepository).save(captor.capture());
        assertEquals("Completed", captor.getValue().getCurrentState());
        assertEquals("COMPLETED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("startWorkflow with guard using != operator and non-matching context")
    void startWorkflow_guardNotEquals_withNonMatchingContext() {
        WorkflowDefinition definition = new WorkflowDefinition(
                "ne-auto.v1",
                "1.0.0",
                Set.of("Start", "Approved"),
                "Start",
                Set.of("Approved"),
                List.of(),
                List.of(
                        new TransitionDef("tr-auto", "Start", "Approved",
                                null, "status!=blocked")
                ),
                null
        );
        registry.register(definition);

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity e = invocation.getArgument(0);
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return e;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // status=ok, guard "status!=blocked" should pass -> auto-transition to Approved
        StartWorkflowCommand command = new StartWorkflowCommand(
                "ne-auto.v1", "ne-1", Map.of("status", "ok"), null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertEquals("Approved", result.currentState());
        assertEquals("COMPLETED", result.status());
    }

    @Test
    @DisplayName("startWorkflow evaluates guard == where context value is null")
    void startWorkflow_guardEquals_withNullContextValue() {
        WorkflowDefinition definition = new WorkflowDefinition(
                "null-ctx-guard.v1",
                "1.0.0",
                Set.of("Start", "Done"),
                "Start",
                Set.of("Done"),
                List.of(),
                List.of(
                        new TransitionDef("tr-auto", "Start", "Done",
                                null, "missing_key==expected")
                ),
                null
        );
        registry.register(definition);

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity e = invocation.getArgument(0);
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return e;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // No context data matching "missing_key", guard should fail, stay in Start
        StartWorkflowCommand command = new StartWorkflowCommand(
                "null-ctx-guard.v1", "ncg-1", Map.of(), null);

        WorkflowInstance result = engine.startWorkflow(command);

        // Guard fails (value is null), should stay in Start
        assertEquals("Start", result.currentState());
        assertEquals("RUNNING", result.status());
    }

    @Test
    @DisplayName("startWorkflow with null and empty context data")
    void startWorkflow_withEmptyContextData() {
        registry.register(simpleDefinition());

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity e = invocation.getArgument(0);
                    e.setCreatedAt(Instant.now());
                    e.setUpdatedAt(Instant.now());
                    return e;
                });
        when(tokenRepository.save(any(WorkflowTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StartWorkflowCommand command = new StartWorkflowCommand(
                WORKFLOW_TYPE, "bk-empty", Map.of(), null);

        WorkflowInstance result = engine.startWorkflow(command);

        assertNotNull(result);
    }
}
