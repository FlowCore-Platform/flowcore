package io.flowcore.runtime.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.WorkflowEngine;
import io.flowcore.api.dto.CompleteStepCommand;
import io.flowcore.api.dto.SignalWorkflowCommand;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TimerFiredCommand;
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
import io.flowcore.statemachine.compiler.CompiledWorkflow;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of the {@link WorkflowEngine}.
 * Orchestrates the full lifecycle of workflow instances: creation, signal handling,
 * step completion, and timer processing. All operations are transactional and
 * use optimistic locking for concurrency safety.
 */
@Service
public class DefaultWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowEngine.class);
    private static final String MAIN_TOKEN_NAME = "main";
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowTokenRepository tokenRepository;
    private final WorkflowStepExecutionRepository stepExecutionRepository;
    private final DefaultWorkflowRegistry registry;
    private final ObjectMapper objectMapper;

    public DefaultWorkflowEngine(
            WorkflowInstanceRepository instanceRepository,
            WorkflowTokenRepository tokenRepository,
            WorkflowStepExecutionRepository stepExecutionRepository,
            DefaultWorkflowRegistry registry,
            ObjectMapper objectMapper) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.stepExecutionRepository = Objects.requireNonNull(stepExecutionRepository);
        this.registry = Objects.requireNonNull(registry);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // -- startWorkflow -----------------------------------------------------

    @Override
    @Transactional
    public WorkflowInstance startWorkflow(StartWorkflowCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // Check idempotency: if businessKey is provided and instance already exists, return it
        if (command.businessKey() != null && !command.businessKey().isBlank()) {
            Optional<WorkflowInstanceEntity> existing =
                    instanceRepository.findByWorkflowTypeAndBusinessKey(
                            command.workflowType(), command.businessKey());
            if (existing.isPresent()) {
                log.info("Workflow instance already exists for type={}, businessKey={}, returning existing",
                        command.workflowType(), command.businessKey());
                return toDto(existing.get());
            }
        }

        CompiledWorkflow compiled = registry.resolveCompiled(command.workflowType());
        WorkflowDefinition definition = compiled.getDefinition();

        // Create the instance entity
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(UUID.randomUUID());
        instance.setWorkflowType(command.workflowType());
        instance.setBusinessKey(
                command.businessKey() != null ? command.businessKey() : UUID.randomUUID().toString());
        instance.setStatus("RUNNING");
        instance.setCurrentState(definition.initialState());
        instance.setContextJson(serializeContext(command.contextData()));

        instance = instanceRepository.save(instance);

        // Create the main execution token
        WorkflowTokenEntity token = new WorkflowTokenEntity();
        token.setId(UUID.randomUUID());
        token.setWorkflowInstance(instance);
        token.setTokenName(MAIN_TOKEN_NAME);
        token.setActiveNode(definition.initialState());
        token.setStatus("ACTIVE");

        tokenRepository.save(token);

        log.info("Started workflow instance: id={}, type={}, state={}",
                instance.getId(), command.workflowType(), definition.initialState());

        // Evaluate auto-transitions from initial state
        evaluateAutoTransitions(instance, token, compiled);

        return toDto(instance);
    }

    // -- signal ------------------------------------------------------------

    @Override
    @Transactional
    public void signal(SignalWorkflowCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        WorkflowInstanceEntity instance = loadInstanceOrThrow(command.instanceId());
        CompiledWorkflow compiled = registry.resolveCompiled(instance.getWorkflowType());

        // Find transitions matching current state and event type
        List<TransitionDef> candidates = compiled.getTransitionsByFromState()
                .getOrDefault(instance.getCurrentState(), Collections.emptyList());

        for (TransitionDef transition : candidates) {
            if (matchesTrigger(transition, command.eventType())) {
                // Evaluate guard if present
                if (transition.guardExpression() != null
                        && !evaluateGuard(transition.guardExpression(), instance)) {
                    log.debug("Guard expression '{}' did not match for transition {}",
                            transition.guardExpression(), transition.id());
                    continue;
                }

                // Execute the transition
                executeTransition(instance, transition, compiled);

                log.info("Signal processed: instanceId={}, event={}, newState={}",
                        command.instanceId(), command.eventType(), instance.getCurrentState());
                return;
            }
        }

        log.debug("No matching transition for signal: instanceId={}, event={}, currentState={}",
                command.instanceId(), command.eventType(), instance.getCurrentState());
    }

    // -- completeStep ------------------------------------------------------

    @Override
    @Transactional
    public void completeStep(CompleteStepCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        WorkflowInstanceEntity instance = loadInstanceOrThrow(command.instanceId());
        CompiledWorkflow compiled = registry.resolveCompiled(instance.getWorkflowType());

        // Find the step execution
        List<WorkflowStepExecutionEntity> executions =
                stepExecutionRepository.findByWorkflowInstanceIdAndStepId(
                        command.instanceId(), command.stepId());

        if (executions.isEmpty()) {
            throw new IllegalArgumentException(
                    "No step execution found for instanceId=" + command.instanceId()
                            + ", stepId=" + command.stepId());
        }

        // Get the latest execution for this step
        WorkflowStepExecutionEntity execution = executions.get(executions.size() - 1);

        boolean succeeded = command.errorCode() == null && command.errorDetail() == null;

        if (succeeded) {
            execution.setStatus("SUCCEEDED");
            execution.setResultJson(command.resultJson());
        } else {
            execution.setStatus("FAILED");
            execution.setErrorCode(command.errorCode());
            execution.setErrorDetail(command.errorDetail());
            execution.setResultJson(command.resultJson());
        }
        execution.setFinishedAt(Instant.now());

        stepExecutionRepository.save(execution);

        if (succeeded) {
            // Find the step definition and advance state
            StepDef stepDef = compiled.getStepIndex().get(command.stepId());
            if (stepDef != null) {
                instance.setCurrentState(stepDef.toState());

                // Check if we reached a terminal state
                if (compiled.getDefinition().terminalStates().contains(stepDef.toState())) {
                    instance.setStatus("COMPLETED");
                    log.info("Workflow instance reached terminal state: id={}, state={}",
                            instance.getId(), stepDef.toState());
                } else {
                    // Evaluate auto-transitions from new state
                    WorkflowTokenEntity token = tokenRepository.findById(command.tokenId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Token not found: " + command.tokenId()));
                    token.setActiveNode(stepDef.toState());
                    tokenRepository.save(token);
                    evaluateAutoTransitions(instance, token, compiled);
                }
            }

            instanceRepository.save(instance);
        } else {
            // On failure, mark instance as FAILED if no retry policy
            StepDef stepDef = compiled.getStepIndex().get(command.stepId());
            if (stepDef == null || stepDef.retryPolicy() == null) {
                instance.setStatus("FAILED");
                instanceRepository.save(instance);
                log.warn("Step {} failed without retry policy, instance {} marked FAILED",
                        command.stepId(), command.instanceId());
            }
        }

        log.info("Step completed: instanceId={}, stepId={}, succeeded={}",
                command.instanceId(), command.stepId(), succeeded);
    }

    // -- handleTimerFired --------------------------------------------------

    @Override
    @Transactional
    public void handleTimerFired(TimerFiredCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        WorkflowInstanceEntity instance = loadInstanceOrThrow(command.instanceId());
        CompiledWorkflow compiled = registry.resolveCompiled(instance.getWorkflowType());

        // Find transitions triggered by this timer
        List<TransitionDef> candidates = compiled.getTransitionsByFromState()
                .getOrDefault(instance.getCurrentState(), Collections.emptyList());

        for (TransitionDef transition : candidates) {
            TriggerDef trigger = transition.trigger();
            if (trigger != null
                    && "timer".equals(trigger.type())
                    && command.timerName().equals(trigger.name())) {

                executeTransition(instance, transition, compiled);

                log.info("Timer fired: instanceId={}, timer={}, newState={}",
                        command.instanceId(), command.timerName(), instance.getCurrentState());
                return;
            }
        }

        log.debug("No matching transition for timer: instanceId={}, timer={}, currentState={}",
                command.instanceId(), command.timerName(), instance.getCurrentState());
    }

    // -- findInstance -------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowInstance> findInstance(UUID instanceId) {
        return instanceRepository.findById(instanceId).map(this::toDto);
    }

    // -- findByBusinessKey --------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowInstance> findByBusinessKey(String workflowType, String businessKey) {
        return instanceRepository.findByWorkflowTypeAndBusinessKey(workflowType, businessKey)
                .map(this::toDto);
    }

    // -- Private helpers ---------------------------------------------------

    private WorkflowInstanceEntity loadInstanceOrThrow(UUID instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Workflow instance not found: " + instanceId));
    }

    /**
     * Evaluates automatic (event-less) transitions from the current state.
     * An automatic transition has no trigger (or a null trigger type) and
     * is evaluated immediately after entering a new state.
     */
    private void evaluateAutoTransitions(
            WorkflowInstanceEntity instance,
            WorkflowTokenEntity token,
            CompiledWorkflow compiled) {

        boolean advanced;
        int maxIterations = 100; // guard against infinite loops
        int iterations = 0;

        do {
            advanced = false;
            iterations++;
            if (iterations > maxIterations) {
                throw new IllegalStateException(
                        "Auto-transition loop exceeded max iterations for instance " + instance.getId());
            }

            List<TransitionDef> candidates = compiled.getTransitionsByFromState()
                    .getOrDefault(instance.getCurrentState(), Collections.emptyList());

            for (TransitionDef transition : candidates) {
                // Auto-transitions have no trigger
                if (transition.trigger() != null) {
                    continue;
                }

                // Evaluate guard
                if (transition.guardExpression() != null
                        && !evaluateGuard(transition.guardExpression(), instance)) {
                    continue;
                }

                executeTransition(instance, transition, compiled);
                token.setActiveNode(instance.getCurrentState());
                tokenRepository.save(token);
                advanced = true;
                break; // re-evaluate from the new state
            }
        } while (advanced);
    }

    /**
     * Executes a single transition: updates the instance's current state,
     * checks for terminal state, and creates step execution records.
     */
    private void executeTransition(
            WorkflowInstanceEntity instance,
            TransitionDef transition,
            CompiledWorkflow compiled) {

        String previousState = instance.getCurrentState();
        instance.setCurrentState(transition.to());

        log.debug("Transition {} executed: {} -> {}",
                transition.id(), previousState, transition.to());

        // Check if we reached a terminal state
        if (compiled.getDefinition().terminalStates().contains(transition.to())) {
            instance.setStatus("COMPLETED");
            log.info("Workflow instance reached terminal state: id={}, state={}",
                    instance.getId(), transition.to());
        }

        instanceRepository.save(instance);

        // Check if there is a step associated with this transition's from/to states
        StepDef matchingStep = findStepForTransition(compiled, transition);
        if (matchingStep != null) {
            createStepExecution(instance, matchingStep, compiled);
        }
    }

    /**
     * Finds a step definition that corresponds to the given transition's from/to states.
     */
    private StepDef findStepForTransition(CompiledWorkflow compiled, TransitionDef transition) {
        for (StepDef step : compiled.getDefinition().steps()) {
            if (step.fromState().equals(transition.from())
                    && step.toState().equals(transition.to())) {
                return step;
            }
        }
        return null;
    }

    /**
     * Creates a step execution record for the given step.
     */
    private void createStepExecution(
            WorkflowInstanceEntity instance,
            StepDef step,
            CompiledWorkflow compiled) {

        // Find an active token for this instance
        List<WorkflowTokenEntity> activeTokens =
                tokenRepository.findByWorkflowInstanceIdAndStatus(instance.getId(), "ACTIVE");

        WorkflowTokenEntity token = activeTokens.isEmpty()
                ? tokenRepository.findByWorkflowInstanceId(instance.getId())
                        .stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "No token found for instance " + instance.getId()))
                : activeTokens.get(0);

        WorkflowStepExecutionEntity execution = new WorkflowStepExecutionEntity();
        execution.setId(UUID.randomUUID());
        execution.setWorkflowInstance(instance);
        execution.setToken(token);
        execution.setStepId(step.id());
        execution.setAttempt(1);
        execution.setStatus("STARTED");
        execution.setStartedAt(Instant.now());

        stepExecutionRepository.save(execution);
    }

    /**
     * Checks if a transition's trigger matches the given event type.
     */
    private boolean matchesTrigger(TransitionDef transition, String eventType) {
        TriggerDef trigger = transition.trigger();
        if (trigger == null) {
            return false;
        }
        return "signal".equals(trigger.type()) && eventType.equals(trigger.name());
    }

    /**
     * Evaluates a guard expression against the workflow context data.
     * For now, supports simple equality checks of the form {@code key==value}
     * and {@code key!=value}. Returns {@code true} for unparseable expressions.
     */
    private boolean evaluateGuard(String guardExpression, WorkflowInstanceEntity instance) {
        if (guardExpression == null || guardExpression.isBlank()) {
            return true;
        }

        Map<String, Object> contextData = parseContextJson(instance.getContextJson());

        // Simple guard evaluation: support "key==value" and "key!=value"
        if (guardExpression.contains("==")) {
            String[] parts = guardExpression.split("==", 2);
            String key = parts[0].trim();
            String expectedValue = parts[1].trim();
            Object actualValue = contextData.get(key);
            return actualValue != null && expectedValue.equals(String.valueOf(actualValue));
        } else if (guardExpression.contains("!=")) {
            String[] parts = guardExpression.split("!=", 2);
            String key = parts[0].trim();
            String forbiddenValue = parts[1].trim();
            Object actualValue = contextData.get(key);
            return actualValue == null || !forbiddenValue.equals(String.valueOf(actualValue));
        }

        // Unknown guard expression format: allow by default
        log.warn("Unsupported guard expression format '{}', allowing transition", guardExpression);
        return true;
    }

    private String serializeContext(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize context data", e);
        }
    }

    private Map<String, Object> parseContextJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse context JSON, returning empty map", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Converts a JPA entity to an API DTO.
     */
    private WorkflowInstance toDto(WorkflowInstanceEntity entity) {
        return new WorkflowInstance(
                entity.getId(),
                entity.getWorkflowType(),
                entity.getBusinessKey(),
                entity.getStatus(),
                entity.getCurrentState(),
                entity.getVersion() != null ? entity.getVersion().intValue() : 0,
                entity.getContextJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
