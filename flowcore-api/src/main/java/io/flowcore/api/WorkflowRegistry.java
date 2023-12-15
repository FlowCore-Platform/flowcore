package io.flowcore.api;

import io.flowcore.api.dto.WorkflowDefinition;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for workflow definitions.
 */
public interface WorkflowRegistry {

    /**
     * Registers a new workflow definition.
     *
     * @param definition the workflow definition to register
     */
    void register(WorkflowDefinition definition);

    /**
     * Resolves a workflow definition by its type name.
     *
     * @param workflowType the unique workflow type identifier
     * @return the matching definition, or empty if not found
     */
    Optional<WorkflowDefinition> resolve(String workflowType);

    /**
     * Returns all registered workflow definitions.
     *
     * @return an unmodifiable collection of all definitions
     */
    Collection<WorkflowDefinition> all();
}
