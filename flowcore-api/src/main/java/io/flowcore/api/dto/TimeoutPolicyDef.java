package io.flowcore.api.dto;

/**
 * Timeout policy configuration for a workflow step.
 *
 * @param timeoutMs     timeout duration in milliseconds (must be &ge; 100)
 * @param onTimeoutState the target state to transition to on timeout
 */
public record TimeoutPolicyDef(
        long timeoutMs,
        String onTimeoutState
) {
    /**
     * Compact constructor with validation.
     */
    public TimeoutPolicyDef {
        if (timeoutMs < 100) {
            throw new IllegalArgumentException("timeoutMs must be >= 100");
        }
    }
}
