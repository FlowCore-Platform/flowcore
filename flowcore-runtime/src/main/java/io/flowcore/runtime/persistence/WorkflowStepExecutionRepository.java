package io.flowcore.runtime.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WorkflowStepExecutionEntity}.
 */
public interface WorkflowStepExecutionRepository extends JpaRepository<WorkflowStepExecutionEntity, UUID> {

    /**
     * Finds all step executions for a given workflow instance, ordered by start time.
     *
     * @param workflowInstanceId the parent instance UUID
     * @return list of step executions in chronological order
     */
    List<WorkflowStepExecutionEntity> findByWorkflowInstanceIdOrderByStartedAtAsc(UUID workflowInstanceId);

    /**
     * Finds step executions for a specific step within a workflow instance.
     *
     * @param workflowInstanceId the parent instance UUID
     * @param stepId             the step definition identifier
     * @return list of matching executions
     */
    List<WorkflowStepExecutionEntity> findByWorkflowInstanceIdAndStepId(UUID workflowInstanceId, String stepId);

    /**
     * Finds step executions for a specific token within a workflow instance.
     *
     * @param workflowInstanceId the parent instance UUID
     * @param tokenId            the token UUID
     * @return list of matching executions
     */
    List<WorkflowStepExecutionEntity> findByWorkflowInstanceIdAndTokenId(UUID workflowInstanceId, UUID tokenId);
}
