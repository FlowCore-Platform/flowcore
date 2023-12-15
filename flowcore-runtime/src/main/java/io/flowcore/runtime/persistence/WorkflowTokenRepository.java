package io.flowcore.runtime.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WorkflowTokenEntity}.
 */
public interface WorkflowTokenRepository extends JpaRepository<WorkflowTokenEntity, UUID> {

    /**
     * Finds all tokens belonging to the given workflow instance.
     *
     * @param workflowInstanceId the parent instance UUID
     * @return list of tokens for that instance
     */
    List<WorkflowTokenEntity> findByWorkflowInstanceId(UUID workflowInstanceId);

    /**
     * Finds active tokens for the given workflow instance.
     *
     * @param workflowInstanceId the parent instance UUID
     * @param status             the token status to filter by
     * @return list of matching tokens
     */
    List<WorkflowTokenEntity> findByWorkflowInstanceIdAndStatus(UUID workflowInstanceId, String status);
}
