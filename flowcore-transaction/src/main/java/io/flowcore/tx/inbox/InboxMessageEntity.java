package io.flowcore.tx.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code flowcore.inbox_message} table.
 * <p>
 * Stores deduplication records for consumed messages so that
 * the same message is never processed more than once within
 * a given consumer group.
 */
@Entity
@Table(name = "inbox_message", schema = "flowcore")
public class InboxMessageEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "consumer_group", nullable = false)
    private String consumerGroup;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected InboxMessageEntity() {
        // JPA
    }

    private InboxMessageEntity(Builder builder) {
        this.id = builder.id;
        this.source = builder.source;
        this.messageId = builder.messageId;
        this.consumerGroup = builder.consumerGroup;
        this.receivedAt = builder.receivedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getConsumerGroup() { return consumerGroup; }
    public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private String source;
        private String messageId;
        private String consumerGroup;
        private Instant receivedAt;

        private Builder() {}

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder messageId(String messageId) { this.messageId = messageId; return this; }
        public Builder consumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; return this; }
        public Builder receivedAt(Instant receivedAt) { this.receivedAt = receivedAt; return this; }

        public InboxMessageEntity build() { return new InboxMessageEntity(this); }
    }
}
