package io.flowcore.security.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service responsible for persisting audit log entries.
 */
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;

    /**
     * Constructs a new {@code AuditLogService}.
     *
     * @param repository the audit log repository
     */
    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an audit log entry to persistent storage.
     *
     * @param entry the audit log entry to persist
     */
    public void recordAudit(AuditLogEntry entry) {
        log.debug("Recording audit: actor={} action={} decision={}",
                entry.actorSub(), entry.action(), entry.decision());

        AuditLogEntity entity = AuditLogEntity.fromEntry(entry);
        repository.save(entity);
    }
}
