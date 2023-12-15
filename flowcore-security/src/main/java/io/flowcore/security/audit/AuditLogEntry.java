package io.flowcore.security.audit;

import java.time.Instant;
import java.util.List;

/**
 * Domain object representing an audit log entry to be persisted.
 * Used as a data transfer object between the authorization service and the audit persistence layer.
 *
 * @param actorSub     the subject identifier of the actor
 * @param actorRoles   the roles assigned to the actor
 * @param actorTenant  the tenant of the actor
 * @param action       the action that was evaluated
 * @param objectType   the type of the target object
 * @param objectId     the identifier of the target object
 * @param decision     the authorization decision (ALLOW or DENY)
 * @param reason       the human-readable reason for the decision
 * @param traceId      the distributed tracing trace ID
 * @param spanId       the distributed tracing span ID
 * @param requestIp    the IP address of the request
 * @param userAgent    the User-Agent header value
 * @param createdAt    the timestamp when the audit entry was created
 */
public record AuditLogEntry(
        String actorSub,
        List<String> actorRoles,
        String actorTenant,
        String action,
        String objectType,
        String objectId,
        String decision,
        String reason,
        String traceId,
        String spanId,
        String requestIp,
        String userAgent,
        Instant createdAt
) {
    /**
     * Compact constructor ensuring a defensive copy of the roles list.
     */
    public AuditLogEntry {
        actorRoles = actorRoles != null ? List.copyOf(actorRoles) : List.of();
    }
}
