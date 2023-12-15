package io.flowcore.api.dto;

import java.time.Duration;

/**
 * Result of a provider adapter call.
 */
public record ProviderCallResult<T>(
    Status status,
    T response,
    String errorCode,
    String errorDetail,
    Duration retryAfter
) {
    public enum Status { SUCCESS, RETRYABLE_FAILURE, FATAL_FAILURE }

    public static <T> ProviderCallResult<T> success(T response) {
        return new ProviderCallResult<>(Status.SUCCESS, response, null, null, null);
    }
    public static <T> ProviderCallResult<T> retryableFailure(String code, String detail, Duration retryAfter) {
        return new ProviderCallResult<>(Status.RETRYABLE_FAILURE, null, code, detail, retryAfter);
    }
    public static <T> ProviderCallResult<T> fatalFailure(String code, String detail) {
        return new ProviderCallResult<>(Status.FATAL_FAILURE, null, code, detail, null);
    }
}
