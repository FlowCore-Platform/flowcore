package io.flowcore.runtime.query;

import io.flowcore.api.Page;
import io.flowcore.api.Pageable;
import io.flowcore.api.dto.StepExecutionSummary;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.runtime.persistence.WorkflowInstanceEntity;
import io.flowcore.runtime.persistence.WorkflowInstanceRepository;
import io.flowcore.runtime.persistence.WorkflowStepExecutionEntity;
import io.flowcore.runtime.persistence.WorkflowStepExecutionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultWorkflowQueryApi} using mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class DefaultWorkflowQueryApiTest {

    @Mock
    private WorkflowInstanceRepository instanceRepository;

    @Mock
    private WorkflowStepExecutionRepository stepExecutionRepository;

    private DefaultWorkflowQueryApi queryApi;

    @BeforeEach
    void setUp() {
        queryApi = new DefaultWorkflowQueryApi(instanceRepository, stepExecutionRepository);
    }

    // -- Helper methods ----------------------------------------------------

    private WorkflowInstanceEntity buildEntity(UUID id, String type, String businessKey,
                                                String status, String currentState, Long version) {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        entity.setId(id);
        entity.setWorkflowType(type);
        entity.setBusinessKey(businessKey);
        entity.setStatus(status);
        entity.setCurrentState(currentState);
        entity.setVersion(version);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private WorkflowStepExecutionEntity buildStepEntity(String stepId, int attempt,
                                                         String status, Instant startedAt,
                                                         Instant finishedAt) {
        WorkflowStepExecutionEntity entity = new WorkflowStepExecutionEntity();
        entity.setId(UUID.randomUUID());
        entity.setStepId(stepId);
        entity.setAttempt(attempt);
        entity.setStatus(status);
        entity.setStartedAt(startedAt);
        entity.setFinishedAt(finishedAt);
        return entity;
    }

    // -- findById ----------------------------------------------------------

    @Test
    @DisplayName("findById returns instance when found")
    void findById_returnsInstance_whenFound() {
        UUID instanceId = UUID.randomUUID();
        WorkflowInstanceEntity entity = buildEntity(
                instanceId, "order-workflow.v1", "order-100",
                "RUNNING", "Processing", 3L);

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(entity));

        Optional<WorkflowInstance> result = queryApi.findById(instanceId);

        assertTrue(result.isPresent());
        WorkflowInstance dto = result.get();
        assertEquals(instanceId, dto.id());
        assertEquals("order-workflow.v1", dto.workflowType());
        assertEquals("order-100", dto.businessKey());
        assertEquals("RUNNING", dto.status());
        assertEquals("Processing", dto.currentState());
        assertEquals(3, dto.version());
        assertNotNull(dto.createdAt());
        assertNotNull(dto.updatedAt());
    }

    @Test
    @DisplayName("findById maps null version to 0")
    void findById_mapsNullVersionToZero() {
        UUID instanceId = UUID.randomUUID();
        WorkflowInstanceEntity entity = buildEntity(
                instanceId, "order-workflow.v1", "order-200",
                "PENDING", "Start", null);

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(entity));

        Optional<WorkflowInstance> result = queryApi.findById(instanceId);

        assertTrue(result.isPresent());
        assertEquals(0, result.get().version());
    }

    @Test
    @DisplayName("findById returns empty when not found")
    void findById_returnsEmpty_whenNotFound() {
        UUID instanceId = UUID.randomUUID();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.empty());

        Optional<WorkflowInstance> result = queryApi.findById(instanceId);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findById preserves contextJson in DTO")
    void findById_preservesContextJson() {
        UUID instanceId = UUID.randomUUID();
        WorkflowInstanceEntity entity = buildEntity(
                instanceId, "test.v1", "bk-1", "RUNNING", "Start", 1L);
        entity.setContextJson("{\"key\":\"value\"}");

        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(entity));

        Optional<WorkflowInstance> result = queryApi.findById(instanceId);

        assertTrue(result.isPresent());
        assertEquals("{\"key\":\"value\"}", result.get().contextJson());
    }

    // -- findByStatus ------------------------------------------------------

    @Test
    @DisplayName("findByStatus returns paginated results")
    void findByStatus_returnsPaginatedResults() {
        Instant now = Instant.now();
        WorkflowInstanceEntity e1 = buildEntity(UUID.randomUUID(), "order.v1", "b1", "RUNNING", "S1", 0L);
        e1.setCreatedAt(now);
        e1.setUpdatedAt(now);
        WorkflowInstanceEntity e2 = buildEntity(UUID.randomUUID(), "order.v1", "b2", "RUNNING", "S2", 1L);
        e2.setCreatedAt(now);
        e2.setUpdatedAt(now);
        WorkflowInstanceEntity e3 = buildEntity(UUID.randomUUID(), "order.v1", "b3", "RUNNING", "S3", 2L);
        e3.setCreatedAt(now);
        e3.setUpdatedAt(now);

        when(instanceRepository.findByWorkflowTypeAndStatus("order.v1", "RUNNING"))
                .thenReturn(List.of(e1, e2, e3));

        Pageable pageable = Pageable.of(0, 2);
        Page<WorkflowInstance> page = queryApi.findByStatus("order.v1", "RUNNING", pageable);

        assertEquals(2, page.getContent().size());
        assertEquals(0, page.getPageNumber());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertTrue(page.isFirst());
    }

    @Test
    @DisplayName("findByStatus returns second page correctly")
    void findByStatus_returnsSecondPage() {
        Instant now = Instant.now();
        WorkflowInstanceEntity e1 = buildEntity(UUID.randomUUID(), "order.v1", "b1", "RUNNING", "S1", 0L);
        e1.setCreatedAt(now);
        e1.setUpdatedAt(now);
        WorkflowInstanceEntity e2 = buildEntity(UUID.randomUUID(), "order.v1", "b2", "RUNNING", "S2", 1L);
        e2.setCreatedAt(now);
        e2.setUpdatedAt(now);
        WorkflowInstanceEntity e3 = buildEntity(UUID.randomUUID(), "order.v1", "b3", "RUNNING", "S3", 2L);
        e3.setCreatedAt(now);
        e3.setUpdatedAt(now);

        when(instanceRepository.findByWorkflowTypeAndStatus("order.v1", "RUNNING"))
                .thenReturn(List.of(e1, e2, e3));

        Pageable pageable = Pageable.of(1, 2);
        Page<WorkflowInstance> page = queryApi.findByStatus("order.v1", "RUNNING", pageable);

        assertEquals(1, page.getContent().size());
        assertEquals(1, page.getPageNumber());
        assertEquals(3, page.getTotalElements());
        assertTrue(page.isLast());
    }

    @Test
    @DisplayName("findByStatus returns empty page when no results")
    void findByStatus_returnsEmptyPage_whenNoResults() {
        when(instanceRepository.findByWorkflowTypeAndStatus("order.v1", "COMPLETED"))
                .thenReturn(List.of());

        Pageable pageable = Pageable.of(0, 10);
        Page<WorkflowInstance> page = queryApi.findByStatus("order.v1", "COMPLETED", pageable);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
        assertTrue(page.isEmpty());
    }

    @Test
    @DisplayName("findByStatus handles page beyond result set")
    void findByStatus_handlesPageBeyondResultSet() {
        WorkflowInstanceEntity e1 = buildEntity(UUID.randomUUID(), "order.v1", "b1", "RUNNING", "S1", 0L);
        e1.setCreatedAt(Instant.now());
        e1.setUpdatedAt(Instant.now());

        when(instanceRepository.findByWorkflowTypeAndStatus("order.v1", "RUNNING"))
                .thenReturn(List.of(e1));

        Pageable pageable = Pageable.of(5, 10);
        Page<WorkflowInstance> page = queryApi.findByStatus("order.v1", "RUNNING", pageable);

        assertTrue(page.getContent().isEmpty());
        assertEquals(1, page.getTotalElements());
    }

    // -- getStepHistory ----------------------------------------------------

    @Test
    @DisplayName("getStepHistory returns summaries ordered by startedAt")
    void getStepHistory_returnsSummaries() {
        UUID instanceId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T10:05:00Z");

        WorkflowStepExecutionEntity step1 = buildStepEntity("step-validate", 1, "SUCCEEDED", t1, t1.plusMillis(500));
        WorkflowStepExecutionEntity step2 = buildStepEntity("step-process", 1, "STARTED", t2, null);

        when(stepExecutionRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId))
                .thenReturn(List.of(step1, step2));

        List<StepExecutionSummary> summaries = queryApi.getStepHistory(instanceId);

        assertEquals(2, summaries.size());

        StepExecutionSummary s1 = summaries.get(0);
        assertEquals("step-validate", s1.stepId());
        assertEquals(1, s1.attempt());
        assertEquals("SUCCEEDED", s1.status());
        assertEquals(t1, s1.startedAt());
        assertEquals(t1.plusMillis(500), s1.finishedAt());

        StepExecutionSummary s2 = summaries.get(1);
        assertEquals("step-process", s2.stepId());
        assertEquals("STARTED", s2.status());
        assertEquals(t2, s2.startedAt());
        assertTrue(s2.finishedAt() == null);
    }

    @Test
    @DisplayName("getStepHistory returns empty list when no steps found")
    void getStepHistory_returnsEmptyList_whenNoSteps() {
        UUID instanceId = UUID.randomUUID();
        when(stepExecutionRepository.findByWorkflowInstanceIdOrderByStartedAtAsc(instanceId))
                .thenReturn(List.of());

        List<StepExecutionSummary> summaries = queryApi.getStepHistory(instanceId);

        assertTrue(summaries.isEmpty());
    }
}
