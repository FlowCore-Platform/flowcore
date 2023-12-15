package io.flowcore.statemachine.dsl;

import io.flowcore.api.dto.TimeoutPolicyDef;

/**
 * Fluent DSL value for constructing a {@link TimeoutPolicyDef}.
 *
 * <p>Usage: {@code TimeoutPolicy.afterMs(5000).onTimeoutTransition("cancelled")}
 *
 * @param timeoutMs     timeout duration in milliseconds (must be &ge; 100)
 * @param onTimeoutState the target state to transition to on timeout
 */
public record TimeoutPolicy(
        long timeoutMs,
        String onTimeoutState
) {

    /**
     * Compact constructor with validation.
     */
    public TimeoutPolicy {
        if (timeoutMs < 100) {
            throw new IllegalArgumentException("timeoutMs must be >= 100, got: " + timeoutMs);
        }
    }

    /**
     * Start building a timeout policy with the given duration.
     *
     * @param timeoutMs timeout in milliseconds (&ge; 100)
     * @return a partial TimeoutPolicy (onTimeoutState not yet set)
     */
    public static TimeoutPolicy afterMs(long timeoutMs) {
        return new TimeoutPolicy(timeoutMs, null);
    }

    /**
     * Set the state to transition to when the timeout fires.
     *
     * @param state the target state on timeout
     * @return a complete TimeoutPolicy
     */
    public TimeoutPolicy onTimeoutTransition(String state) {
        return new TimeoutPolicy(timeoutMs, state);
    }

    /**
     * Convert this DSL value to an API {@link TimeoutPolicyDef}.
     *
     * @return the corresponding TimeoutPolicyDef
     */
    public TimeoutPolicyDef toApiDto() {
        return new TimeoutPolicyDef(timeoutMs, onTimeoutState);
    }
}
