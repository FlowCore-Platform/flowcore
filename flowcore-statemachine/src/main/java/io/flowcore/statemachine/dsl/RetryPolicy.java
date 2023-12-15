package io.flowcore.statemachine.dsl;

import io.flowcore.api.dto.RetryPolicyDef;

/**
 * Fluent DSL value for constructing a {@link RetryPolicyDef}.
 *
 * <p>Supports two factory modes:
 * <ul>
 *   <li>{@link #fixed(int, long)} - fixed-interval retries</li>
 *   <li>{@link #exponential(int, long, long)} - exponential-backoff retries</li>
 * </ul>
 *
 * @param mode        retry mode ("fixed" or "exponential")
 * @param maxAttempts maximum retry attempts (must be &ge; 1)
 * @param baseDelayMs base delay between retries in milliseconds (must be &ge; 10)
 * @param maxDelayMs  maximum delay cap in milliseconds (must be &ge; baseDelayMs)
 * @param jitterPct   jitter percentage 0-100
 */
public record RetryPolicy(
        String mode,
        int maxAttempts,
        long baseDelayMs,
        long maxDelayMs,
        int jitterPct
) {

    /**
     * Compact constructor with validation.
     */
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        if (baseDelayMs < 10) {
            throw new IllegalArgumentException("baseDelayMs must be >= 10, got: " + baseDelayMs);
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException(
                    "maxDelayMs must be >= baseDelayMs, got maxDelayMs=" + maxDelayMs
                            + ", baseDelayMs=" + baseDelayMs);
        }
        if (jitterPct < 0 || jitterPct > 100) {
            throw new IllegalArgumentException("jitterPct must be 0-100, got: " + jitterPct);
        }
        if (!"fixed".equals(mode) && !"exponential".equals(mode)) {
            throw new IllegalArgumentException("mode must be 'fixed' or 'exponential', got: " + mode);
        }
    }

    /**
     * Create a fixed-interval retry policy.
     *
     * @param maxAttempts maximum retry attempts (&ge; 1)
     * @param delayMs     fixed delay between retries in ms (&ge; 10)
     * @return a fixed retry policy
     */
    public static RetryPolicy fixed(int maxAttempts, long delayMs) {
        return new RetryPolicy("fixed", maxAttempts, delayMs, delayMs, 0);
    }

    /**
     * Create an exponential-backoff retry policy.
     *
     * @param maxAttempts maximum retry attempts (&ge; 1)
     * @param baseDelayMs initial delay in ms (&ge; 10)
     * @param maxDelayMs  maximum delay cap in ms (&ge; baseDelayMs)
     * @return an exponential retry policy
     */
    public static RetryPolicy exponential(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        return new RetryPolicy("exponential", maxAttempts, baseDelayMs, maxDelayMs, 0);
    }

    /**
     * Set jitter percentage (0-100).
     *
     * @param pct jitter percentage
     * @return a new RetryPolicy with the given jitter
     */
    public RetryPolicy withJitter(int pct) {
        return new RetryPolicy(mode, maxAttempts, baseDelayMs, maxDelayMs, pct);
    }

    /**
     * Convert this DSL value to an API {@link RetryPolicyDef}.
     * Converts jitterPct from int (0-100) to double (0.0-1.0).
     *
     * @return the corresponding RetryPolicyDef
     */
    public RetryPolicyDef toApiDto() {
        return new RetryPolicyDef(mode, maxAttempts, baseDelayMs, maxDelayMs, jitterPct / 100.0);
    }
}
