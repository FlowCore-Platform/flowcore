package io.flowcore.api.dto;

/**
 * Retry policy configuration for a workflow step.
 *
 * @param mode        retry mode (e.g. "fixed", "exponential")
 * @param maxAttempts maximum number of retry attempts (must be &ge; 1)
 * @param baseDelayMs base delay between retries in milliseconds
 * @param maxDelayMs  maximum delay cap in milliseconds
 * @param jitterPct   jitter percentage (0.0 – 1.0) to randomize retry delay
 */
public record RetryPolicyDef(
        String mode,
        int maxAttempts,
        long baseDelayMs,
        long maxDelayMs,
        double jitterPct
) {
    /**
     * Compact constructor with validation.
     */
    public RetryPolicyDef {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
    }
}
