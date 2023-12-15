package io.flowcore.api.dto;

/**
 * Result of an authorization evaluation.
 */
public record AuthorizationDecision(boolean allowed, String reason) {
    public static AuthorizationDecision allow(String reason) { return new AuthorizationDecision(true, reason); }
    public static AuthorizationDecision deny(String reason) { return new AuthorizationDecision(false, reason); }
}
