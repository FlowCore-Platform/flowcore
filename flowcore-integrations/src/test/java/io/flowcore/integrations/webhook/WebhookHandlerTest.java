package io.flowcore.integrations.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.InboxService;
import io.flowcore.api.WebhookVerifier;
import io.flowcore.api.dto.InboxAcceptResult;
import io.flowcore.api.dto.ProviderCallResult;
import io.flowcore.api.dto.WebhookVerificationRequest;
import io.flowcore.api.dto.WebhookVerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookHandlerTest {

    private ObjectMapper objectMapper;
    private WebhookVerifier webhookVerifier;
    private InboxService inboxService;
    private WebhookHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookVerifier = mock(WebhookVerifier.class);
        inboxService = mock(InboxService.class);
        handler = new WebhookHandler(objectMapper, webhookVerifier, inboxService);
    }

    private WebhookVerificationRequest createRequest(String body) {
        return new WebhookVerificationRequest(
                "POST",
                "/webhooks/stripe",
                Map.of(
                        "X-Signature", "sig-123",
                        "X-Message-Id", "msg-unique-001",
                        "X-Webhook-Source", "stripe"
                ),
                body.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @DisplayName("Successful webhook verification and acceptance")
    void verifyAndAccept_success_returnsParsedPayload() {
        WebhookVerificationRequest request = createRequest("{\"event\":\"payment.completed\",\"amount\":100}");

        when(webhookVerifier.verify(request)).thenReturn(WebhookVerificationResult.success());
        when(inboxService.tryAccept("stripe", "msg-unique-001", "webhook-handler"))
                .thenReturn(new InboxAcceptResult(true, UUID.randomUUID()));

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handler.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
        assertNotNull(result.response());
        assertEquals("payment.completed", result.response().get("event").asText());
        assertEquals(100, result.response().get("amount").asInt());
        assertNull(result.errorCode());
    }

    @Test
    @DisplayName("Webhook with invalid signature returns FATAL_FAILURE")
    void verifyAndAccept_invalidSignature_returnsFatalFailure() {
        WebhookVerificationRequest request = createRequest("{\"event\":\"test\"}");

        when(webhookVerifier.verify(request))
                .thenReturn(WebhookVerificationResult.invalid("Invalid HMAC signature"));

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handler.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.FATAL_FAILURE, result.status());
        assertEquals("WEBHOOK_VERIFICATION_FAILED", result.errorCode());
        assertNotNull(result.errorDetail());
        assertTrue(result.errorDetail().contains("Invalid HMAC signature"));

        verify(inboxService, never()).tryAccept(any(), any(), any());
    }

    @Test
    @DisplayName("Duplicate webhook returns FATAL_FAILURE")
    void verifyAndAccept_duplicate_returnsFatalFailure() {
        WebhookVerificationRequest request = createRequest("{\"event\":\"test\"}");

        when(webhookVerifier.verify(request)).thenReturn(WebhookVerificationResult.success());
        when(inboxService.tryAccept("stripe", "msg-unique-001", "webhook-handler"))
                .thenReturn(new InboxAcceptResult(false, null));

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handler.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.FATAL_FAILURE, result.status());
        assertEquals("WEBHOOK_DUPLICATE", result.errorCode());
        assertTrue(result.errorDetail().contains("msg-unique-001"));
    }

    @Test
    @DisplayName("Invalid JSON body returns FATAL_FAILURE")
    void verifyAndAccept_invalidJson_returnsFatalFailure() {
        WebhookVerificationRequest request = createRequest("not valid json");

        when(webhookVerifier.verify(request)).thenReturn(WebhookVerificationResult.success());
        when(inboxService.tryAccept(any(), any(), any()))
                .thenReturn(new InboxAcceptResult(true, UUID.randomUUID()));

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handler.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.FATAL_FAILURE, result.status());
        assertEquals("WEBHOOK_PARSE_ERROR", result.errorCode());
        assertNotNull(result.errorDetail());
    }

    @Test
    @DisplayName("Handler without WebhookVerifier skips verification")
    void verifyAndAccept_noVerifier_skipsVerification() {
        WebhookHandler handlerNoVerifier = new WebhookHandler(objectMapper, null, inboxService);
        WebhookVerificationRequest request = createRequest("{\"event\":\"test\"}");

        when(inboxService.tryAccept("stripe", "msg-unique-001", "webhook-handler"))
                .thenReturn(new InboxAcceptResult(true, UUID.randomUUID()));

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handlerNoVerifier.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
        assertNotNull(result.response());
    }

    @Test
    @DisplayName("Handler without InboxService skips deduplication")
    void verifyAndAccept_noInboxService_skipsDedup() {
        WebhookHandler handlerNoInbox = new WebhookHandler(objectMapper, webhookVerifier, null);
        WebhookVerificationRequest request = createRequest("{\"event\":\"test\"}");

        when(webhookVerifier.verify(request)).thenReturn(WebhookVerificationResult.success());

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handlerNoInbox.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
        assertNotNull(result.response());
    }

    @Test
    @DisplayName("Handler without both optional dependencies still works")
    void verifyAndAccept_noDependencies_worksAsPassThrough() {
        WebhookHandler minimalHandler = new WebhookHandler(objectMapper, null, null);
        WebhookVerificationRequest request = createRequest("{\"status\":\"ok\"}");

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                minimalHandler.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
        assertEquals("ok", result.response().get("status").asText());
    }

    @Test
    @DisplayName("Fallback message ID from body when X-Message-Id header is absent")
    void verifyAndAccept_noMessageIdHeader_usesFallback() {
        WebhookVerificationRequest request = new WebhookVerificationRequest(
                "POST",
                "/webhooks/test",
                Map.of("X-Webhook-Source", "custom"),
                "{\"data\":1}".getBytes(StandardCharsets.UTF_8)
        );

        when(webhookVerifier.verify(request)).thenReturn(WebhookVerificationResult.success());
        when(inboxService.tryAccept(eq("custom"), any(String.class), eq("webhook-handler")))
                .thenReturn(new InboxAcceptResult(true, UUID.randomUUID()));

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handler.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
        verify(inboxService).tryAccept(eq("custom"), any(String.class), eq("webhook-handler"));
    }

    @Test
    @DisplayName("Source extraction from path when X-Webhook-Source is absent")
    void verifyAndAccept_noSourceHeader_usesPathAsSource() {
        WebhookVerificationRequest request = new WebhookVerificationRequest(
                "POST",
                "/webhooks/provider-a",
                Map.of("X-Message-Id", "msg-123"),
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)
        );

        when(webhookVerifier.verify(request)).thenReturn(WebhookVerificationResult.success());
        when(inboxService.tryAccept(eq("/webhooks/provider-a"), eq("msg-123"), eq("webhook-handler")))
                .thenReturn(new InboxAcceptResult(true, UUID.randomUUID()));

        ProviderCallResult<com.fasterxml.jackson.databind.JsonNode> result =
                handler.verifyAndAccept(request);

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
    }
}
