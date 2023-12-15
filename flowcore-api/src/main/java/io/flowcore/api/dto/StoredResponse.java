package io.flowcore.api.dto;

/**
 * Represents a previously stored idempotent response.
 *
 * @param scope        the logical scope
 * @param key          the idempotency key
 * @param requestHash  hash of the original request
 * @param responseJson the serialized response JSON
 */
public record StoredResponse(
        String scope,
        String key,
        String requestHash,
        String responseJson
) {}
