package io.flowcore.integrations.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.InboxService;
import io.flowcore.api.WebhookVerifier;
import io.flowcore.api.dto.InboxAcceptResult;
import io.flowcore.api.dto.ProviderCallResult;
import io.flowcore.api.dto.WebhookVerificationRequest;
import io.flowcore.api.dto.WebhookVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Handles incoming webhook requests with verification, deduplication,
 * and payload parsing.
 * <p>
 * Depends on {@link WebhookVerifier} and {@link InboxService} as optional
 * beans. If either is not available, the corresponding step is skipped
 * (verification or deduplication).
 */
@Component
public class WebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookHandler.class);

    private final ObjectMapper objectMapper;

    private final WebhookVerifier webhookVerifier;
    private final InboxService inboxService;

    /**
     * Constructs a WebhookHandler with optional dependencies.
     *
     * @param objectMapper     the JSON object mapper for payload parsing
     * @param webhookVerifier  optional webhook signature verifier
     * @param inboxService     optional inbox service for deduplication
     */
    public WebhookHandler(
            ObjectMapper objectMapper,
            @Autowired(required = false) WebhookVerifier webhookVerifier,
            @Autowired(required = false) InboxService inboxService
    ) {
        this.objectMapper = objectMapper;
        this.webhookVerifier = webhookVerifier;
        this.inboxService = inboxService;
    }

    /**
     * Verifies and accepts an incoming webhook.
     * <p>
     * Steps:
     * <ol>
     *   <li>Verify webhook signature (if a {@link WebhookVerifier} is available)</li>
     *   <li>Deduplicate via {@link InboxService} (if available)</li>
     *   <li>Parse the payload as JSON</li>
     * </ol>
     *
     * @param request the webhook verification request containing method, path,
     *                headers, and body
     * @return the parsed webhook payload as a JsonNode, wrapped in a ProviderCallResult
     */
    public ProviderCallResult<JsonNode> verifyAndAccept(WebhookVerificationRequest request) {
        // Step 1: Verify signature
        if (webhookVerifier != null) {
            WebhookVerificationResult result = webhookVerifier.verify(request);
            if (!result.valid()) {
                log.warn("Webhook verification failed: {}", result.errorMessage());
                return ProviderCallResult.fatalFailure(
                        "WEBHOOK_VERIFICATION_FAILED",
                        result.errorMessage()
                );
            }
            log.debug("Webhook signature verified successfully");
        } else {
            log.debug("No WebhookVerifier configured, skipping signature verification");
        }

        // Step 2: Deduplicate
        if (inboxService != null) {
            String messageId = extractMessageId(request);
            String source = extractSource(request);
            String consumerGroup = "webhook-handler";

            InboxAcceptResult acceptResult = inboxService.tryAccept(
                    source, messageId, consumerGroup
            );
            if (!acceptResult.accepted()) {
                log.info("Webhook deduplicated: source={}, messageId={}", source, messageId);
                return ProviderCallResult.fatalFailure(
                        "WEBHOOK_DUPLICATE",
                        "Webhook with messageId " + messageId + " already processed"
                );
            }
            log.debug("Webhook accepted: source={}, messageId={}", source, messageId);
        } else {
            log.debug("No InboxService configured, skipping deduplication");
        }

        // Step 3: Parse payload
        try {
            JsonNode payload = objectMapper.readTree(request.body());
            log.debug("Webhook payload parsed successfully");
            return ProviderCallResult.success(payload);
        } catch (Exception ex) {
            log.error("Failed to parse webhook payload: {}", ex.getMessage());
            return ProviderCallResult.fatalFailure(
                    "WEBHOOK_PARSE_ERROR",
                    "Failed to parse webhook body: " + ex.getMessage()
            );
        }
    }

    private String extractMessageId(WebhookVerificationRequest request) {
        if (request.headers() != null) {
            String messageId = request.headers().get("X-Message-Id");
            if (messageId != null) {
                return messageId;
            }
        }
        // Fallback: generate a deterministic ID from body hash
        return UUID.nameUUIDFromBytes(request.body()).toString();
    }

    private String extractSource(WebhookVerificationRequest request) {
        if (request.headers() != null) {
            String source = request.headers().get("X-Webhook-Source");
            if (source != null) {
                return source;
            }
        }
        return request.path();
    }
}
