package io.flowcore.api;

import java.util.Map;
import java.util.UUID;

/**
 * Provides access to the runtime context of a workflow instance.
 * Implementations are not required to be thread-safe; they are typically
 * scoped to a single workflow operation.
 */
public interface WorkflowContext {

    /**
     * @return the workflow type this context belongs to
     */
    String getWorkflowType();

    /**
     * @return the unique identifier of the workflow instance
     */
    UUID getInstanceId();

    /**
     * @return the application-level business key, or {@code null} if not set
     */
    String getBusinessKey();

    /**
     * @return the current state name in the state machine
     */
    String getCurrentState();

    /**
     * Returns a snapshot of the context data map.
     *
     * @return unmodifiable map of context data
     */
    Map<String, Object> getContextData();

    /**
     * Sets or overwrites a context data entry.
     *
     * @param key   the data key
     * @param value the data value
     */
    void setContextData(String key, Object value);

    /**
     * Removes a context data entry if it exists.
     *
     * @param key the data key to remove
     */
    void removeContextData(String key);

    /**
     * Increments the optimistic-locking version and returns the new value.
     *
     * @return the updated version number
     */
    int incrementAndGetVersion();
}
