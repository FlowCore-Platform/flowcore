package io.flowcore.runtime.registry;

import io.flowcore.api.WorkflowRegistry;
import io.flowcore.api.dto.WorkflowDefinition;
import io.flowcore.statemachine.compiler.CompiledWorkflow;
import io.flowcore.statemachine.compiler.WorkflowCompiler;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry that compiles and stores workflow definitions.
 * Thread-safe via {@link ConcurrentHashMap}.
 */
@org.springframework.stereotype.Service
public class DefaultWorkflowRegistry implements WorkflowRegistry {

    private final ConcurrentHashMap<String, CompiledWorkflow> compiledWorkflows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkflowDefinition> definitions = new ConcurrentHashMap<>();
    private final WorkflowCompiler compiler;

    public DefaultWorkflowRegistry(WorkflowCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates and compiles the definition before storing it.
     * If a definition with the same {@code workflowType} already exists,
     * it is replaced.</p>
     *
     * @param definition the workflow definition to register
     * @throws io.flowcore.statemachine.validation.WorkflowValidationException if validation fails
     */
    @Override
    public void register(WorkflowDefinition definition) {
        CompiledWorkflow compiled = compiler.compile(definition);
        compiledWorkflows.put(definition.workflowType(), compiled);
        definitions.put(definition.workflowType(), definition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WorkflowDefinition> resolve(String workflowType) {
        return Optional.ofNullable(definitions.get(workflowType));
    }

    /**
     * Resolves the compiled workflow by type.
     *
     * @param workflowType the unique workflow type identifier
     * @return the compiled workflow
     * @throws IllegalArgumentException if the workflow type is not registered
     */
    public CompiledWorkflow resolveCompiled(String workflowType) {
        CompiledWorkflow compiled = compiledWorkflows.get(workflowType);
        if (compiled == null) {
            throw new IllegalArgumentException(
                    "No workflow registered with type: " + workflowType);
        }
        return compiled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<WorkflowDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    /**
     * Returns all compiled workflows.
     *
     * @return unmodifiable collection of compiled workflows
     */
    public Collection<CompiledWorkflow> allCompiled() {
        return Collections.unmodifiableCollection(compiledWorkflows.values());
    }
}
