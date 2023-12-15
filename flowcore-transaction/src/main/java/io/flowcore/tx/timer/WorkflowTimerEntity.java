package io.flowcore.tx.timer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code flowcore.workflow_timer} table.
 * <p>
 * Represents a scheduled timer bound to a workflow instance and token.
 * When the timer fires, a {@code TimerFiredCommand} event is published
 * through the outbox.
 */
@Entity
@Table(name = "workflow_timer", schema = "flowcore")
public class WorkflowTimerEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_instance_id", nullable = false)
    private UUID workflowInstanceId;

    @Column(name = "token_id", nullable = false)
    private UUID tokenId;

    @Column(name = "timer_name", nullable = false)
    private String timerName;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkflowTimerEntity() {
        // JPA
    }

    private WorkflowTimerEntity(Builder builder) {
        this.id = builder.id;
        this.workflowInstanceId = builder.workflowInstanceId;
        this.tokenId = builder.tokenId;
        this.timerName = builder.timerName;
        this.dueAt = builder.dueAt;
        this.payloadJson = builder.payloadJson;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getWorkflowInstanceId() { return workflowInstanceId; }
    public void setWorkflowInstanceId(UUID workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; }

    public UUID getTokenId() { return tokenId; }
    public void setTokenId(UUID tokenId) { this.tokenId = tokenId; }

    public String getTimerName() { return timerName; }
    public void setTimerName(String timerName) { this.timerName = timerName; }

    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private UUID workflowInstanceId;
        private UUID tokenId;
        private String timerName;
        private Instant dueAt;
        private String payloadJson;
        private String status;
        private Instant createdAt;

        private Builder() {}

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder workflowInstanceId(UUID workflowInstanceId) { this.workflowInstanceId = workflowInstanceId; return this; }
        public Builder tokenId(UUID tokenId) { this.tokenId = tokenId; return this; }
        public Builder timerName(String timerName) { this.timerName = timerName; return this; }
        public Builder dueAt(Instant dueAt) { this.dueAt = dueAt; return this; }
        public Builder payloadJson(String payloadJson) { this.payloadJson = payloadJson; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public WorkflowTimerEntity build() { return new WorkflowTimerEntity(this); }
    }
}
