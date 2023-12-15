package io.flowcore.runtime.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WorkflowInstanceEntity}.
 */
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, UUID> {

    /**
     * Finds a workflow instance by its workflow type and business key.
     *
     * @param workflowType the workflow type
     * @param businessKey  the application-level correlation key
     * @return the matching instance, if any
     */
    Optional<WorkflowInstanceEntity> findByWorkflowTypeAndBusinessKey(String workflowType, String businessKey);

    /**
     * Finds all workflow instances with the given status.
     *
     * @param status the lifecycle status to filter by
     * @return list of matching instances
     */
    List<WorkflowInstanceEntity> findByStatus(String status);

    /**
     * Finds all workflow instances of the given type with the given status.
     *
     * @param workflowType the workflow type
     * @param status       the lifecycle status
     * @return list of matching instances
     */
    List<WorkflowInstanceEntity> findByWorkflowTypeAndStatus(String workflowType, String status);

    /**
     * Counts workflow instances of the given type with the given status.
     *
     * @param workflowType the workflow type
     * @param status       the lifecycle status
     * @return the count of matching instances
     */
    long countByWorkflowTypeAndStatus(String workflowType, String status);
}
