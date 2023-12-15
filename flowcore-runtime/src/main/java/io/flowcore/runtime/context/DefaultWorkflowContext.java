package io.flowcore.runtime.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.WorkflowContext;
import io.flowcore.runtime.persistence.WorkflowInstanceEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link WorkflowContext} backed by a {@link WorkflowInstanceEntity}.
 * Manages context data as a JSON-serialized map and handles optimistic-locking version increments.
 *
 * <p>This implementation is NOT thread-safe; it is scoped to a single workflow operation.</p>
 */
public class DefaultWorkflowContext implements WorkflowContext {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private final WorkflowInstanceEntity entity;
    private final ObjectMapper objectMapper;
    private Map<String, Object> contextData;

    /**
     * Creates a new context wrapping the given entity.
     *
     * @param entity        the workflow instance entity
     * @param objectMapper  Jackson ObjectMapper for JSON serialization
     */
    public DefaultWorkflowContext(WorkflowInstanceEntity entity, ObjectMapper objectMapper) {
        this.entity = Objects.requireNonNull(entity, "entity must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.contextData = parseContextJson(entity.getContextJson());
    }

    @Override
    public String getWorkflowType() {
        return entity.getWorkflowType();
    }

    @Override
    public UUID getInstanceId() {
        return entity.getId();
    }

    @Override
    public String getBusinessKey() {
        return entity.getBusinessKey();
    }

    @Override
    public String getCurrentState() {
        return entity.getCurrentState();
    }

    @Override
    public Map<String, Object> getContextData() {
        return Collections.unmodifiableMap(contextData);
    }

    @Override
    public void setContextData(String key, Object value) {
        contextData.put(key, value);
        syncToEntity();
    }

    @Override
    public void removeContextData(String key) {
        contextData.remove(key);
        syncToEntity();
    }

    @Override
    public int incrementAndGetVersion() {
        Long current = entity.getVersion();
        long next = (current != null ? current : 0L) + 1;
        entity.setVersion(next);
        return (int) next;
    }

    /**
     * Returns the underlying entity for direct JPA persistence operations.
     *
     * @return the workflow instance entity
     */
    public WorkflowInstanceEntity getEntity() {
        return entity;
    }

    // -- Private helpers ---------------------------------------------------

    private Map<String, Object> parseContextJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return new HashMap<>(parsed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to parse context JSON for instance " + entity.getId(), e);
        }
    }

    private void syncToEntity() {
        try {
            entity.setContextJson(objectMapper.writeValueAsString(contextData));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize context data for instance " + entity.getId(), e);
        }
    }
}
