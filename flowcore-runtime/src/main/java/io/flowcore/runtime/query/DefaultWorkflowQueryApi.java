package io.flowcore.runtime.query;

import io.flowcore.api.Page;
import io.flowcore.api.Pageable;
import io.flowcore.api.WorkflowQueryApi;
import io.flowcore.api.dto.StepExecutionSummary;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.runtime.persistence.WorkflowInstanceEntity;
import io.flowcore.runtime.persistence.WorkflowInstanceRepository;
import io.flowcore.runtime.persistence.WorkflowStepExecutionEntity;
import io.flowcore.runtime.persistence.WorkflowStepExecutionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of the read-only {@link WorkflowQueryApi}.
 * Provides paginated queries for workflow instances and step execution history.
 */
@Service
public class DefaultWorkflowQueryApi implements WorkflowQueryApi {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowStepExecutionRepository stepExecutionRepository;

    public DefaultWorkflowQueryApi(
            WorkflowInstanceRepository instanceRepository,
            WorkflowStepExecutionRepository stepExecutionRepository) {
        this.instanceRepository = instanceRepository;
        this.stepExecutionRepository = stepExecutionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowInstance> findById(UUID instanceId) {
        return instanceRepository.findById(instanceId)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WorkflowInstance> findByStatus(String workflowType, String status, Pageable pageable) {
        List<WorkflowInstanceEntity> allMatching =
                instanceRepository.findByWorkflowTypeAndStatus(workflowType, status);

        long totalElements = allMatching.size();
        int fromIndex = (int) Math.min(pageable.offset(), totalElements);
        int toIndex = (int) Math.min(fromIndex + pageable.pageSize(), totalElements);

        List<WorkflowInstance> pageContent = allMatching.subList(fromIndex, toIndex)
                .stream()
                .map(this::toDto)
                .toList();

        return new Page<>(
                pageContent,
                pageable.pageNumber(),
                pageable.pageSize(),
                totalElements
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<StepExecutionSummary> getStepHistory(UUID instanceId) {
        return stepExecutionRepository
                .findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    // -- Conversion helpers ------------------------------------------------

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

    private StepExecutionSummary toSummary(WorkflowStepExecutionEntity entity) {
        return new StepExecutionSummary(
                entity.getStepId(),
                entity.getAttempt(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }
}
