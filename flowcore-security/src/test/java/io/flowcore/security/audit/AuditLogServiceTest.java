package io.flowcore.security.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogServiceTest {

    private AuditLogRepository repository;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogService(repository);
    }

    @Test
    @DisplayName("should persist audit log entry")
    void shouldPersistAuditLogEntry() {
        AuditLogEntry entry = new AuditLogEntry(
                "user-123",
                List.of("ADMIN", "OPERATOR"),
                "tenant-1",
                "workflow.start",
                "workflow",
                "wf-001",
                "ALLOW",
                "Policy match",
                "trace-1",
                "span-1",
                "10.0.0.1",
                "TestAgent/1.0",
                Instant.now()
        );

        service.recordAudit(entry);

        verify(repository).save(any(AuditLogEntity.class));
    }

    @Test
    @DisplayName("should persist entry with null optional fields")
    void shouldPersistEntryWithNullOptionals() {
        AuditLogEntry entry = new AuditLogEntry(
                "user-123",
                List.of(),
                null,
                "workflow.view",
                "workflow",
                "wf-002",
                "DENY",
                "No match",
                null,
                null,
                null,
                null,
                Instant.now()
        );

        service.recordAudit(entry);

        verify(repository).save(any(AuditLogEntity.class));
    }
}
