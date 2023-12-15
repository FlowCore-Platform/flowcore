package io.flowcore.api.dto;

/**
 * Result of webhook signature verification.
 */
public record WebhookVerificationResult(boolean valid, String errorMessage) {
    public static WebhookVerificationResult success() { return new WebhookVerificationResult(true, null); }
    public static WebhookVerificationResult invalid(String reason) { return new WebhookVerificationResult(false, reason); }
}
