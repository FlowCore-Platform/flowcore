package io.flowcore.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping for the {@code flowcore.workflow_token} table.
 * Represents a concurrency handle (token) used in fork/join scenarios
 * within a workflow instance.
 */
@Entity
@Table(name = "workflow_token", schema = "flowcore")
public class WorkflowTokenEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstanceEntity workflowInstance;

    @Column(name = "token_name", nullable = false)
    private String tokenName;

    @Column(name = "active_node")
    private String activeNode;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkflowTokenEntity() {
        // JPA requires a no-arg constructor
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // -- Getters and setters -----------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WorkflowInstanceEntity getWorkflowInstance() {
        return workflowInstance;
    }

    public void setWorkflowInstance(WorkflowInstanceEntity workflowInstance) {
        this.workflowInstance = workflowInstance;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstance != null ? workflowInstance.getId() : null;
    }

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public String getActiveNode() {
        return activeNode;
    }

    public void setActiveNode(String activeNode) {
        this.activeNode = activeNode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
