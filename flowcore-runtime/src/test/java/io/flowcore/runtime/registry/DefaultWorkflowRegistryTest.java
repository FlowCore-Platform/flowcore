package io.flowcore.runtime.registry;

import io.flowcore.api.dto.StepDef;
import io.flowcore.api.dto.TransitionDef;
import io.flowcore.api.dto.WorkflowDefinition;
import io.flowcore.statemachine.compiler.WorkflowCompiler;
import io.flowcore.statemachine.validation.WorkflowValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultWorkflowRegistry}.
 */
class DefaultWorkflowRegistryTest {

    private DefaultWorkflowRegistry registry;

    private static WorkflowDefinition createValidDefinition(String type) {
        return new WorkflowDefinition(
                type,
                "1.0.0",
                Set.of("Start", "Processing", "Done"),
                "Start",
                Set.of("Done"),
                List.of(
                        new StepDef("step-1", "task", "Start", "Processing", null, null, null, null),
                        new StepDef("step-2", "task", "Processing", "Done", null, null, null, null)
                ),
                List.of(),
                null
        );
    }

    @BeforeEach
    void setUp() {
        registry = new DefaultWorkflowRegistry(
                new WorkflowCompiler(new WorkflowValidator()));
    }

    @Test
    @DisplayName("register stores a valid workflow definition")
    void register_storesValidDefinition() {
        WorkflowDefinition definition = createValidDefinition("my-workflow.v1");

        registry.register(definition);

        Optional<WorkflowDefinition> resolved = registry.resolve("my-workflow.v1");
        assertTrue(resolved.isPresent());
        assertEquals("my-workflow.v1", resolved.get().workflowType());
    }

    @Test
    @DisplayName("resolve returns empty for unknown workflow type")
    void resolve_returnsEmptyForUnknown() {
        Optional<WorkflowDefinition> resolved = registry.resolve("non-existent.v1");
        assertFalse(resolved.isPresent());
    }

    @Test
    @DisplayName("resolveCompiled throws for unknown workflow type")
    void resolveCompiled_throwsForUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolveCompiled("non-existent.v1"));
    }

    @Test
    @DisplayName("resolveCompiled returns compiled workflow for known type")
    void resolveCompiled_returnsCompiledForKnown() {
        WorkflowDefinition definition = createValidDefinition("compiled-test.v1");
        registry.register(definition);

        var compiled = registry.resolveCompiled("compiled-test.v1");
        assertNotNull(compiled);
        assertEquals("compiled-test.v1", compiled.getDefinition().workflowType());
        assertNotNull(compiled.getAdjacencyMap());
        assertNotNull(compiled.getStepIndex());
        assertNotNull(compiled.getTransitionsByFromState());
        assertNotNull(compiled.getReachableStates());
    }

    @Test
    @DisplayName("all returns empty collection when nothing registered")
    void all_returnsEmptyWhenNothingRegistered() {
        assertTrue(registry.all().isEmpty());
    }

    @Test
    @DisplayName("all returns all registered definitions")
    void all_returnsAllRegistered() {
        registry.register(createValidDefinition("workflow-a.v1"));
        registry.register(createValidDefinition("workflow-b.v1"));

        var all = registry.all();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("register replaces existing definition with same type")
    void register_replacesExistingDefinition() {
        registry.register(createValidDefinition("replace-test.v1"));

        WorkflowDefinition updated = new WorkflowDefinition(
                "replace-test.v1",
                "2.0.0",
                Set.of("Start", "Done"),
                "Start",
                Set.of("Done"),
                List.of(new StepDef("step-fast", "task", "Start", "Done", null, null, null, null)),
                List.of(),
                null
        );
        registry.register(updated);

        Optional<WorkflowDefinition> resolved = registry.resolve("replace-test.v1");
        assertTrue(resolved.isPresent());
        assertEquals("2.0.0", resolved.get().version());
    }

    @Test
    @DisplayName("allCompiled returns compiled versions of all workflows")
    void allCompiled_returnsAllCompiled() {
        registry.register(createValidDefinition("compiled-a.v1"));
        registry.register(createValidDefinition("compiled-b.v1"));

        var allCompiled = registry.allCompiled();
        assertEquals(2, allCompiled.size());
    }

    @Test
    @DisplayName("register throws for invalid definition")
    void register_throwsForInvalidDefinition() {
        // Missing required states
        WorkflowDefinition invalid = new WorkflowDefinition(
                "ab", // too short
                "1.0.0",
                Set.of("Start", "Done"),
                "Start",
                Set.of("Done"),
                List.of(),
                List.of(),
                null
        );

        assertThrows(io.flowcore.statemachine.validation.WorkflowValidationException.class,
                () -> registry.register(invalid));
    }
}
