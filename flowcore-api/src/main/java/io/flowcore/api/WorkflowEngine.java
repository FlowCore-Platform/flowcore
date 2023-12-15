package io.flowcore.api;

import io.flowcore.api.dto.CompleteStepCommand;
import io.flowcore.api.dto.SignalWorkflowCommand;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.TimerFiredCommand;
import io.flowcore.api.dto.WorkflowInstance;

import java.util.Optional;
import java.util.UUID;

/**
 * Core engine interface for workflow lifecycle operations.
 */
public interface WorkflowEngine {

    /**
     * Starts a new workflow instance.
     *
     * @param command the start command containing workflow type and parameters
     * @return the newly created workflow instance
     */
    WorkflowInstance startWorkflow(StartWorkflowCommand command);

    /**
     * Sends a signal (external event) to a running workflow instance.
     *
     * @param command the signal command
     */
    void signal(SignalWorkflowCommand command);

    /**
     * Completes a step execution within a workflow instance.
     *
     * @param command the complete-step command
     */
    void completeStep(CompleteStepCommand command);

    /**
     * Handles a timer-fired event for a workflow instance.
     *
     * @param command the timer-fired command
     */
    void handleTimerFired(TimerFiredCommand command);

    /**
     * Finds a workflow instance by its unique identifier.
     *
     * @param instanceId the instance UUID
     * @return the workflow instance, or empty if not found
     */
    Optional<WorkflowInstance> findInstance(UUID instanceId);

    /**
     * Finds a workflow instance by its business key.
     *
     * @param workflowType the workflow type
     * @param businessKey  the application-level correlation key
     * @return the workflow instance, or empty if not found
     */
    Optional<WorkflowInstance> findByBusinessKey(String workflowType, String businessKey);
}
