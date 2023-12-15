package io.flowcore.tx.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code flowcore.idempotency_key} table.
 * <p>
 * Stores request hashes and cached responses so that duplicate requests
 * within the same scope are served from the stored response.
 */
@Entity
@Table(name = "idempotency_key", schema = "flowcore")
public class IdempotencyKeyEntity {

    @Id
    private UUID id;

    private String scope;

    private String key;

    private String requestHash;

    @Column(columnDefinition = "jsonb")
    private String responseJson;

    private Instant createdAt;

    private Instant expiresAt;

    protected IdempotencyKeyEntity() {
        // JPA
    }

    private IdempotencyKeyEntity(Builder builder) {
        this.id = builder.id;
        this.scope = builder.scope;
        this.key = builder.key;
        this.requestHash = builder.requestHash;
        this.responseJson = builder.responseJson;
        this.createdAt = builder.createdAt;
        this.expiresAt = builder.expiresAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private String scope;
        private String key;
        private String requestHash;
        private String responseJson;
        private Instant createdAt;
        private Instant expiresAt;

        private Builder() {}

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder scope(String scope) { this.scope = scope; return this; }
        public Builder key(String key) { this.key = key; return this; }
        public Builder requestHash(String requestHash) { this.requestHash = requestHash; return this; }
        public Builder responseJson(String responseJson) { this.responseJson = responseJson; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }

        public IdempotencyKeyEntity build() { return new IdempotencyKeyEntity(this); }
    }
}
