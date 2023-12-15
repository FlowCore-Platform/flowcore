package io.flowcore.security.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditLogEntity}.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
}
