package io.flowcore.api.dto;

/**
 * Failure information for an outbox event or provider call.
 *
 * @param errorCode    machine-readable error code
 * @param errorDetail  human-readable error detail
 */
public record FailureInfo(
        String errorCode,
        String errorDetail
) {}
