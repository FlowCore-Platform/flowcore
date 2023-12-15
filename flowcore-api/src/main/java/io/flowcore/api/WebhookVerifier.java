package io.flowcore.api;

import io.flowcore.api.dto.WebhookVerificationRequest;
import io.flowcore.api.dto.WebhookVerificationResult;

/**
 * Verifies incoming webhook requests (signature, timestamp, etc.).
 */
public interface WebhookVerifier {

    /**
     * Verifies the authenticity and integrity of a webhook request.
     *
     * @param request the webhook verification request containing method, path, headers, and body
     * @return the verification result
     */
    WebhookVerificationResult verify(WebhookVerificationRequest request);
}
