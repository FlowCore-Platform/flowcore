package io.flowcore.security.authz;

import io.flowcore.security.audit.AuditLogEntry;
import io.flowcore.security.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Default implementation of {@link AuthorizationService}.
 * <p>
 * Delegates policy evaluation to a {@link DefaultPolicyEvaluator} and records
 * every authorization decision through {@link AuditLogService}.
 */
public class DefaultAuthorizationService implements AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthorizationService.class);

    private final PolicyEvaluator policyEvaluator;
    private final AuditLogService auditLogService;

    /**
     * Constructs a new {@code DefaultAuthorizationService}.
     *
     * @param policyEvaluator  the policy evaluator to delegate to
     * @param auditLogService  the audit log service for recording decisions
     */
    public DefaultAuthorizationService(PolicyEvaluator policyEvaluator,
                                       AuditLogService auditLogService) {
        this.policyEvaluator = policyEvaluator;
        this.auditLogService = auditLogService;
    }

    @Override
    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        log.debug("Evaluating authorization: subject={} action={} object={}/{}",
                request.subjectSub(), request.action(),
                request.objectAttributes().objectType(),
                request.objectAttributes().objectId());

        AuthorizationDecision decision = policyEvaluator.evaluate(
                request.subjectRoles(),
                request.objectAttributes(),
                request.action(),
                request.environment()
        );

        recordAudit(request, decision);

        log.debug("Authorization decision: allowed={} reason={}",
                decision.allowed(), decision.reason());

        return decision;
    }

    private void recordAudit(AuthorizationRequest request, AuthorizationDecision decision) {
        try {
            AuditLogEntry entry = new AuditLogEntry(
                    request.subjectSub(),
                    request.subjectRoles(),
                    request.subjectTenant(),
                    request.action(),
                    request.objectAttributes().objectType(),
                    request.objectAttributes().objectId(),
                    decision.allowed() ? "ALLOW" : "DENY",
                    decision.reason(),
                    request.environment().getOrDefault("traceId", null),
                    request.environment().getOrDefault("spanId", null),
                    request.environment().getOrDefault("requestIp", null),
                    request.environment().getOrDefault("userAgent", null),
                    Instant.now()
            );
            auditLogService.recordAudit(entry);
        } catch (Exception ex) {
            log.warn("Failed to record audit log entry: {}", ex.getMessage(), ex);
        }
    }
}
