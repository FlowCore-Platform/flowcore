package io.flowcore.runtime.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.dto.StartWorkflowCommand;
import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.WorkflowDefinition;
import io.flowcore.api.dto.WorkflowInstance;
import io.flowcore.runtime.persistence.WorkflowInstanceEntity;
import io.flowcore.runtime.persistence.WorkflowInstanceRepository;
import io.flowcore.runtime.persistence.WorkflowStepExecutionRepository;
import io.flowcore.runtime.persistence.WorkflowTokenRepository;
import io.flowcore.runtime.registry.DefaultWorkflowRegistry;
import io.flowcore.statemachine.compiler.WorkflowCompiler;
import io.flowcore.statemachine.validation.WorkflowValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests the serializable invariant: concurrent startWorkflow calls
 * for the same (type, businessKey) should result in exactly one success
 * when the database enforces the unique constraint.
 */
@ExtendWith(MockitoExtension.class)
class SerializableInvariantTest {

    @Mock
    private WorkflowInstanceRepository instanceRepository;

    @Mock
    private WorkflowTokenRepository tokenRepository;

    @Mock
    private WorkflowStepExecutionRepository stepExecutionRepository;

    private DefaultWorkflowEngine engine;

    private static final String WORKFLOW_TYPE = "concurrent-test.v1";

    @BeforeEach
    void setUp() {
        WorkflowValidator validator = new WorkflowValidator();
        WorkflowCompiler compiler = new WorkflowCompiler(validator);
        DefaultWorkflowRegistry registry = new DefaultWorkflowRegistry(compiler);

        // Register a simple workflow
        WorkflowDefinition definition = new WorkflowDefinition(
                WORKFLOW_TYPE, "1.0.0",
                Set.of("Start", "Done"),
                "Start",
                Set.of("Done"),
                List.of(new StepDef("step-1", "task", "Start", "Done", null, null, null, null)),
                List.of(),
                null
        );
        registry.register(definition);

        engine = new DefaultWorkflowEngine(
                instanceRepository, tokenRepository, stepExecutionRepository,
                registry, new ObjectMapper());
    }

    @Test
    @DisplayName("Concurrent startWorkflow for same businessKey results in one success")
    void concurrentStart_onlyOneSucceeds() throws Exception {
        String businessKey = "order-concurrent-001";
        int threadCount = 10;

        // Simulate the unique constraint: only the first call returns empty,
        // subsequent calls return the existing entity
        AtomicInteger callCount = new AtomicInteger(0);
        ConcurrentHashMap<String, WorkflowInstanceEntity> store = new ConcurrentHashMap<>();

        when(instanceRepository.findByWorkflowTypeAndBusinessKey(WORKFLOW_TYPE, businessKey))
                .thenAnswer(invocation -> {
                    // Simulate race: all threads see "not found" initially
                    // but only one can actually insert
                    return Optional.empty();
                });

        when(instanceRepository.save(any(WorkflowInstanceEntity.class)))
                .thenAnswer(invocation -> {
                    WorkflowInstanceEntity entity = invocation.getArgument(0);
                    // Simulate unique constraint: only first insert succeeds
                    if (store.putIfAbsent(businessKey, entity) == null) {
                        callCount.incrementAndGet();
                        entity.setCreatedAt(Instant.now());
                        entity.setUpdatedAt(Instant.now());
                        return entity;
                    } else {
                        // Simulate a DataIntegrityViolationException from unique constraint
                        throw new org.springframework.dao.DataIntegrityViolationException(
                                "Duplicate key for (workflow_type, business_key)");
                    }
                });

        when(tokenRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    StartWorkflowCommand cmd = new StartWorkflowCommand(
                            WORKFLOW_TYPE, businessKey, Map.of(), null);
                    engine.startWorkflow(cmd);
                    successes.incrementAndGet();
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    failures.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one thread should succeed in creating the instance
        assertEquals(1, successes.get(), "Exactly one thread should succeed");
        assertEquals(threadCount - 1, failures.get(),
                "All other threads should fail with constraint violation");
        assertTrue(store.containsKey(businessKey), "Instance should be stored");
    }
}
