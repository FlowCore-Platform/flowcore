package io.flowcore.security.authz;

/**
 * Result of an authorization evaluation.
 */
public record AuthorizationDecision(
        boolean allowed,
        String reason
) {

    /**
     * Factory for an ALLOW decision.
     *
     * @param reason human-readable reason
     * @return an ALLOW decision
     */
    public static AuthorizationDecision allow(String reason) {
        return new AuthorizationDecision(true, reason);
    }

    /**
     * Factory for a DENY decision.
     *
     * @param reason human-readable reason
     * @return a DENY decision
     */
    public static AuthorizationDecision deny(String reason) {
        return new AuthorizationDecision(false, reason);
    }
}
