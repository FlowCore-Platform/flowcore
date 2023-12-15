package io.flowcore.security.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogEntityTest {

    @Test
    @DisplayName("should create entity from entry")
    void shouldCreateEntityFromEntry() {
        Instant now = Instant.now();
        AuditLogEntry entry = new AuditLogEntry(
                "user-123",
                List.of("ADMIN"),
                "tenant-1",
                "workflow.start",
                "workflow",
                "wf-001",
                "ALLOW",
                "OK",
                "trace-1",
                "span-1",
                "10.0.0.1",
                "TestAgent/1.0",
                now
        );

        AuditLogEntity entity = AuditLogEntity.fromEntry(entry);

        assertNotNull(entity.getId());
        assertEquals("user-123", entity.getActorSub());
        assertEquals(1, entity.getActorRoles().size());
        assertEquals("ADMIN", entity.getActorRoles().get(0));
        assertEquals("tenant-1", entity.getActorTenant());
        assertEquals("workflow.start", entity.getAction());
        assertEquals("workflow", entity.getObjectType());
        assertEquals("wf-001", entity.getObjectId());
        assertEquals("ALLOW", entity.getDecision());
        assertEquals("OK", entity.getReason());
        assertEquals("trace-1", entity.getTraceId());
        assertEquals("span-1", entity.getSpanId());
        assertEquals("10.0.0.1", entity.getRequestIp());
        assertEquals("TestAgent/1.0", entity.getUserAgent());
        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    @DisplayName("should set and get all fields")
    void shouldSetAndGetAllFields() {
        AuditLogEntity entity = new AuditLogEntity();
        UUID id = UUID.randomUUID();

        entity.setId(id);
        entity.setActorSub("sub-1");
        entity.setActorRoles(List.of("ROLE_A", "ROLE_B"));
        entity.setActorTenant("tenant-x");
        entity.setAction("action");
        entity.setObjectType("type");
        entity.setObjectId("obj-1");
        entity.setDecision("DENY");
        entity.setReason("test reason");
        entity.setTraceId("tid");
        entity.setSpanId("sid");
        entity.setRequestIp("192.168.1.1");
        entity.setUserAgent("Agent");
        entity.setCreatedAt(Instant.EPOCH);
        entity.setDetailsJson("{\"key\":\"value\"}");

        assertEquals(id, entity.getId());
        assertEquals("sub-1", entity.getActorSub());
        assertEquals(2, entity.getActorRoles().size());
        assertEquals("tenant-x", entity.getActorTenant());
        assertEquals("action", entity.getAction());
        assertEquals("type", entity.getObjectType());
        assertEquals("obj-1", entity.getObjectId());
        assertEquals("DENY", entity.getDecision());
        assertEquals("test reason", entity.getReason());
        assertEquals("tid", entity.getTraceId());
        assertEquals("sid", entity.getSpanId());
        assertEquals("192.168.1.1", entity.getRequestIp());
        assertEquals("Agent", entity.getUserAgent());
        assertEquals(Instant.EPOCH, entity.getCreatedAt());
        assertEquals("{\"key\":\"value\"}", entity.getDetailsJson());
    }

    @Test
    @DisplayName("should handle null actor roles in setter")
    void shouldHandleNullActorRoles() {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActorRoles(null);
        assertNotNull(entity.getActorRoles());
        assertTrue(entity.getActorRoles().isEmpty());
    }
}
