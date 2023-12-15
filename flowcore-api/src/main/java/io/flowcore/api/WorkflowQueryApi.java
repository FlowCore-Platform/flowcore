package io.flowcore.api;

import io.flowcore.api.dto.StepExecutionSummary;
import io.flowcore.api.dto.WorkflowInstance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only query API for workflow data.
 */
public interface WorkflowQueryApi {

    /**
     * Finds a workflow instance by its unique identifier.
     *
     * @param instanceId the instance UUID
     * @return the workflow instance, or empty if not found
     */
    Optional<WorkflowInstance> findById(UUID instanceId);

    /**
     * Finds workflow instances of a given type filtered by status, paginated.
     *
     * @param workflowType the workflow type
     * @param status       the status to filter by
     * @param pageable     pagination parameters
     * @return a page of matching workflow instances
     */
    Page<WorkflowInstance> findByStatus(String workflowType, String status, Pageable pageable);

    /**
     * Retrieves the step execution history for a workflow instance.
     *
     * @param instanceId the instance UUID
     * @return ordered list of step execution summaries
     */
    List<StepExecutionSummary> getStepHistory(UUID instanceId);
}
