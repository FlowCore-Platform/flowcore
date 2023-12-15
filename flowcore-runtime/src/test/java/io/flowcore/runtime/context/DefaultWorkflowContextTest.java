package io.flowcore.runtime.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.runtime.persistence.WorkflowInstanceEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DefaultWorkflowContext}.
 */
class DefaultWorkflowContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WorkflowInstanceEntity entity;

    @BeforeEach
    void setUp() {
        entity = new WorkflowInstanceEntity();
        entity.setId(UUID.randomUUID());
        entity.setWorkflowType("order-workflow.v1");
        entity.setBusinessKey("order-001");
        entity.setCurrentState("Processing");
        entity.setVersion(5L);
    }

    // -- Construction and getters ------------------------------------------

    @Test
    @DisplayName("constructor rejects null entity")
    void constructor_rejectsNullEntity() {
        assertThrows(NullPointerException.class,
                () -> new DefaultWorkflowContext(null, objectMapper));
    }

    @Test
    @DisplayName("constructor rejects null objectMapper")
    void constructor_rejectsNullObjectMapper() {
        assertThrows(NullPointerException.class,
                () -> new DefaultWorkflowContext(entity, null));
    }

    @Test
    @DisplayName("getWorkflowType returns entity workflow type")
    void getWorkflowType_returnsEntityWorkflowType() {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        assertEquals("order-workflow.v1", ctx.getWorkflowType());
    }

    @Test
    @DisplayName("getInstanceId returns entity id")
    void getInstanceId_returnsEntityId() {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        assertEquals(entity.getId(), ctx.getInstanceId());
    }

    @Test
    @DisplayName("getBusinessKey returns entity business key")
    void getBusinessKey_returnsEntityBusinessKey() {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        assertEquals("order-001", ctx.getBusinessKey());
    }

    @Test
    @DisplayName("getCurrentState returns entity current state")
    void getCurrentState_returnsEntityCurrentState() {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        assertEquals("Processing", ctx.getCurrentState());
    }

    @Test
    @DisplayName("getEntity returns the underlying entity")
    void getEntity_returnsUnderlyingEntity() {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        assertNotNull(ctx.getEntity());
        assertEquals(entity.getId(), ctx.getEntity().getId());
    }

    // -- Context data parsing and access -----------------------------------

    @Test
    @DisplayName("getContextData returns empty map when contextJson is null")
    void getContextData_returnsEmptyMap_whenContextJsonIsNull() {
        entity.setContextJson(null);
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);

        assertTrue(ctx.getContextData().isEmpty());
    }

    @Test
    @DisplayName("getContextData returns empty map when contextJson is blank")
    void getContextData_returnsEmptyMap_whenContextJsonIsBlank() {
        entity.setContextJson("   ");
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);

        assertTrue(ctx.getContextData().isEmpty());
    }

    @Test
    @DisplayName("getContextData returns parsed JSON values")
    void getContextData_returnsParsedJsonValues() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(Map.of("amount", 100, "name", "test"));
        entity.setContextJson(json);

        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        Map<String, Object> data = ctx.getContextData();

        assertEquals(100, data.get("amount"));
        assertEquals("test", data.get("name"));
    }

    @Test
    @DisplayName("getContextData returns unmodifiable map")
    void getContextData_returnsUnmodifiableMap() {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        Map<String, Object> data = ctx.getContextData();

        assertThrows(UnsupportedOperationException.class,
                () -> data.put("key", "value"));
    }

    @Test
    @DisplayName("constructor throws IllegalStateException on invalid JSON")
    void constructor_throwsOnInvalidJson() {
        entity.setContextJson("{invalid json!!!");
        assertThrows(IllegalStateException.class,
                () -> new DefaultWorkflowContext(entity, objectMapper));
    }

    // -- setContextData ----------------------------------------------------

    @Test
    @DisplayName("setContextData adds new key and syncs to entity")
    void setContextData_addsNewKey_andSyncsToEntity() throws JsonProcessingException {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);

        ctx.setContextData("newKey", "newValue");

        assertEquals("newValue", ctx.getContextData().get("newKey"));

        // Verify entity contextJson was updated
        String updatedJson = entity.getContextJson();
        assertNotNull(updatedJson);
        Map<String, Object> parsed = objectMapper.readValue(updatedJson, Map.class);
        assertEquals("newValue", parsed.get("newKey"));
    }

    @Test
    @DisplayName("setContextData overwrites existing key")
    void setContextData_overwritesExistingKey() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(Map.of("amount", 100));
        entity.setContextJson(json);

        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        assertEquals(100, ctx.getContextData().get("amount"));

        ctx.setContextData("amount", 200);

        assertEquals(200, ctx.getContextData().get("amount"));

        // Verify entity was synced
        Map<String, Object> parsed = objectMapper.readValue(entity.getContextJson(), Map.class);
        assertEquals(200, parsed.get("amount"));
    }

    // -- removeContextData -------------------------------------------------

    @Test
    @DisplayName("removeContextData removes existing key and syncs to entity")
    void removeContextData_removesExistingKey() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(Map.of("amount", 100, "name", "test"));
        entity.setContextJson(json);

        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        ctx.removeContextData("amount");

        assertTrue(ctx.getContextData().isEmpty() || !ctx.getContextData().containsKey("amount"));
        assertEquals(1, ctx.getContextData().size());

        // Verify entity was synced
        Map<String, Object> parsed = objectMapper.readValue(entity.getContextJson(), Map.class);
        assertTrue(!parsed.containsKey("amount"));
    }

    @Test
    @DisplayName("removeContextData is a no-op for non-existent key")
    void removeContextData_noOp_forNonExistentKey() {
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);
        // Should not throw
        ctx.removeContextData("nonExistent");
        assertTrue(ctx.getContextData().isEmpty());
    }

    // -- incrementAndGetVersion --------------------------------------------

    @Test
    @DisplayName("incrementAndGetVersion increments and returns new version")
    void incrementAndGetVersion_incrementsAndReturns() {
        entity.setVersion(5L);
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);

        int result = ctx.incrementAndGetVersion();

        assertEquals(6, result);
        assertEquals(6L, entity.getVersion());
    }

    @Test
    @DisplayName("incrementAndGetVersion starts from 0 when version is null")
    void incrementAndGetVersion_startsFromZero_whenVersionIsNull() {
        entity.setVersion(null);
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);

        int result = ctx.incrementAndGetVersion();

        assertEquals(1, result);
        assertEquals(1L, entity.getVersion());
    }

    @Test
    @DisplayName("incrementAndGetVersion can be called multiple times")
    void incrementAndGetVersion_canBeCalledMultipleTimes() {
        entity.setVersion(0L);
        DefaultWorkflowContext ctx = new DefaultWorkflowContext(entity, objectMapper);

        assertEquals(1, ctx.incrementAndGetVersion());
        assertEquals(2, ctx.incrementAndGetVersion());
        assertEquals(3, ctx.incrementAndGetVersion());
        assertEquals(3L, entity.getVersion());
    }
}
