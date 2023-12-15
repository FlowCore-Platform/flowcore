package io.flowcore.security.audit;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a row in the {@code flowcore.audit_log} table.
 */
@Entity
@Table(name = "audit_log", schema = "flowcore")
public class AuditLogEntity {

    @Id
    private UUID id;

    @Column(name = "actor_sub")
    private String actorSub;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "audit_log_actor_roles",
            schema = "flowcore",
            joinColumns = @JoinColumn(name = "audit_log_id")
    )
    @Column(name = "actor_role")
    private List<String> actorRoles = new ArrayList<>();

    @Column(name = "actor_tenant")
    private String actorTenant;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "object_type", nullable = false)
    private String objectType;

    @Column(name = "object_id", nullable = false)
    private String objectId;

    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "reason")
    private String reason;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "span_id")
    private String spanId;

    @Column(name = "request_ip")
    private String requestIp;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "details_json", columnDefinition = "jsonb")
    private String detailsJson;

    /**
     * Default constructor required by JPA.
     */
    protected AuditLogEntity() {
    }

    /**
     * Creates a new {@code AuditLogEntity} from an {@link AuditLogEntry}.
     *
     * @param entry the audit log entry to convert
     * @return a new entity instance
     */
    public static AuditLogEntity fromEntry(AuditLogEntry entry) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.id = UUID.randomUUID();
        entity.actorSub = entry.actorSub();
        entity.actorRoles = new ArrayList<>(entry.actorRoles());
        entity.actorTenant = entry.actorTenant();
        entity.action = entry.action();
        entity.objectType = entry.objectType();
        entity.objectId = entry.objectId();
        entity.decision = entry.decision();
        entity.reason = entry.reason();
        entity.traceId = entry.traceId();
        entity.spanId = entry.spanId();
        entity.requestIp = entry.requestIp();
        entity.userAgent = entry.userAgent();
        entity.createdAt = entry.createdAt();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getActorSub() {
        return actorSub;
    }

    public void setActorSub(String actorSub) {
        this.actorSub = actorSub;
    }

    public List<String> getActorRoles() {
        return actorRoles;
    }

    public void setActorRoles(List<String> actorRoles) {
        this.actorRoles = actorRoles != null ? new ArrayList<>(actorRoles) : new ArrayList<>();
    }

    public String getActorTenant() {
        return actorTenant;
    }

    public void setActorTenant(String actorTenant) {
        this.actorTenant = actorTenant;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }
}
