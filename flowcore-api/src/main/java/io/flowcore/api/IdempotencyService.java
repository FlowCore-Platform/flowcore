package io.flowcore.api;

import io.flowcore.api.dto.StoredResponse;

import java.time.Instant;
import java.util.Optional;

/**
 * Idempotency service for storing and retrieving request/response pairs.
 */
public interface IdempotencyService {

    /**
     * Finds a previously stored response for the given idempotency scope and key.
     *
     * @param scope       the logical scope (e.g. consumer group or operation name)
     * @param key         the idempotency key
     * @param requestHash a hash of the request payload for collision detection
     * @return the stored response if found, or empty
     */
    Optional<StoredResponse> find(String scope, String key, String requestHash);

    /**
     * Stores a response for future idempotent retrieval.
     *
     * @param scope        the logical scope
     * @param key          the idempotency key
     * @param requestHash  a hash of the request payload
     * @param responseJson the serialized response JSON
     * @param expiresAt    the expiration timestamp, or {@code null} for no expiration
     * @return the stored response record
     */
    StoredResponse store(String scope, String key, String requestHash, String responseJson, Instant expiresAt);
}
