package io.flowcore.api.dto;

import java.util.Map;

/**
 * Request to verify a webhook signature.
 */
public record WebhookVerificationRequest(
    String method,
    String path,
    Map<String, String> headers,
    byte[] body
) {}
