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
 * JPA entity mapping for the {@code flowcore.workflow_step_execution} table.
 * Represents a single step execution attempt within a workflow instance.
 */
@Entity
@Table(name = "workflow_step_execution", schema = "flowcore")
public class WorkflowStepExecutionEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstanceEntity workflowInstance;

    @ManyToOne(optional = false)
    @JoinColumn(name = "token_id", nullable = false)
    private WorkflowTokenEntity token;

    @Column(name = "step_id", nullable = false)
    private String stepId;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_detail")
    private String errorDetail;

    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    public WorkflowStepExecutionEntity() {
        // JPA requires a no-arg constructor
    }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
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

    public WorkflowTokenEntity getToken() {
        return token;
    }

    public void setToken(WorkflowTokenEntity token) {
        this.token = token;
    }

    public UUID getTokenId() {
        return token != null ? token.getId() : null;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }
}
