package io.flowcore.tx.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code flowcore.outbox_event} table.
 * <p>
 * Represents a transactional outbox event awaiting publication to an external broker.
 */
@Entity
@Table(name = "outbox_event", schema = "flowcore")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "headers_json", columnDefinition = "jsonb")
    private String headersJson;

    @Column(nullable = false)
    private String status;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
        // JPA
    }

    private OutboxEventEntity(Builder builder) {
        this.id = builder.id;
        this.aggregateType = builder.aggregateType;
        this.aggregateId = builder.aggregateId;
        this.eventType = builder.eventType;
        this.eventKey = builder.eventKey;
        this.payloadJson = builder.payloadJson;
        this.headersJson = builder.headersJson;
        this.status = builder.status;
        this.publishAttempts = builder.publishAttempts;
        this.nextAttemptAt = builder.nextAttemptAt;
        this.createdAt = builder.createdAt;
        this.publishedAt = builder.publishedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getPublishAttempts() { return publishAttempts; }
    public void setPublishAttempts(int publishAttempts) { this.publishAttempts = publishAttempts; }

    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private String aggregateType;
        private String aggregateId;
        private String eventType;
        private String eventKey;
        private String payloadJson;
        private String headersJson;
        private String status;
        private int publishAttempts;
        private Instant nextAttemptAt;
        private Instant createdAt;
        private Instant publishedAt;

        private Builder() {}

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
        public Builder aggregateId(String aggregateId) { this.aggregateId = aggregateId; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder eventKey(String eventKey) { this.eventKey = eventKey; return this; }
        public Builder payloadJson(String payloadJson) { this.payloadJson = payloadJson; return this; }
        public Builder headersJson(String headersJson) { this.headersJson = headersJson; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder publishAttempts(int publishAttempts) { this.publishAttempts = publishAttempts; return this; }
        public Builder nextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder publishedAt(Instant publishedAt) { this.publishedAt = publishedAt; return this; }

        public OutboxEventEntity build() { return new OutboxEventEntity(this); }
    }
}
