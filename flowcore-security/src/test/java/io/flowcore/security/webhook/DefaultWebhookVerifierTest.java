package io.flowcore.security.webhook;

import io.flowcore.api.dto.WebhookVerificationRequest;
import io.flowcore.api.dto.WebhookVerificationResult;
import io.flowcore.security.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWebhookVerifierTest {

    private static final String SHARED_SECRET = "test-secret-key-12345";

    private SecurityProperties properties;
    private DefaultWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        properties.setWebhookMaxSkewSeconds(300);
        verifier = new DefaultWebhookVerifier(properties, SHARED_SECRET);
    }

    private String computeSignature(String signatureBase) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                SHARED_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmac = mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmac);
    }

    private Map<String, String> buildHeaders(String method, String path, String body,
                                              Instant created) throws Exception {
        // Build signature base
        String signatureBase = "\"@method\": " + method + "\n"
                + "\"@path\": " + path + "\n";

        String label = "sig-params";
        String createdPart = "created=" + created.getEpochSecond() + ";keyid=\"" + SHARED_SECRET + "\"";
        signatureBase += "\"@signature-params\": " + label + ";" + createdPart;

        String signatureValue = computeSignature(signatureBase);

        Map<String, String> headers = new HashMap<>();
        headers.put("Signature-Input",
                label + "=(\"@method\" \"@path\");" + createdPart);
        headers.put("Signature", label + ":" + signatureValue + ":");

        return headers;
    }

    @Nested
    @DisplayName("Valid signature tests")
    class ValidSignatureTests {

        @Test
        @DisplayName("should pass with valid signature and fresh timestamp")
        void shouldPassWithValidSignature() throws Exception {
            Instant now = Instant.now();
            Map<String, String> headers = buildHeaders("POST", "/webhooks/events", "{}", now);

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertTrue(result.valid());
        }

        @Test
        @DisplayName("should pass with timestamp within skew window")
        void shouldPassWithinSkewWindow() throws Exception {
            Instant slightlyOld = Instant.now().minusSeconds(299);
            Map<String, String> headers = buildHeaders("POST", "/webhooks/events", "{}", slightlyOld);

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("Invalid signature tests")
    class InvalidSignatureTests {

        @Test
        @DisplayName("should reject tampered signature")
        void shouldRejectTamperedSignature() throws Exception {
            Instant now = Instant.now();
            Map<String, String> headers = buildHeaders("POST", "/webhooks/events", "{}", now);

            // Tamper with signature by completely replacing the signature value
            headers.put("Signature", "sig-params:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=:");

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertFalse(result.valid());
        }

        @Test
        @DisplayName("should reject wrong secret")
        void shouldRejectWrongSecret() throws Exception {
            DefaultWebhookVerifier wrongVerifier = new DefaultWebhookVerifier(
                    properties, "wrong-secret");

            Instant now = Instant.now();
            Map<String, String> headers = buildHeaders("POST", "/webhooks/events", "{}", now);

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = wrongVerifier.verify(request);

            assertFalse(result.valid());
        }
    }

    @Nested
    @DisplayName("Expired timestamp tests")
    class ExpiredTimestampTests {

        @Test
        @DisplayName("should reject expired timestamp")
        void shouldRejectExpiredTimestamp() throws Exception {
            Instant expired = Instant.now().minusSeconds(301);
            Map<String, String> headers = buildHeaders("POST", "/webhooks/events", "{}", expired);

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("skew") || result.errorMessage().contains("Timestamp"));
        }
    }

    @Nested
    @DisplayName("Missing headers tests")
    class MissingHeadersTests {

        @Test
        @DisplayName("should reject when Signature-Input header is missing")
        void shouldRejectMissingSignatureInput() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Signature", "sig-params:dGVzdA==:");

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("Signature-Input"));
        }

        @Test
        @DisplayName("should reject when Signature header is missing")
        void shouldRejectMissingSignature() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Signature-Input", "sig-params=(\"@method\");created=" + Instant.now().getEpochSecond());

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("Signature"));
        }

        @Test
        @DisplayName("should reject when headers map is null")
        void shouldRejectNullHeaders() {
            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", null, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertFalse(result.valid());
        }

        @Test
        @DisplayName("should reject when headers map is empty")
        void shouldRejectEmptyHeaders() {
            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", Map.of(), "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertFalse(result.valid());
        }

        @Test
        @DisplayName("should reject when created timestamp is missing in Signature-Input")
        void shouldRejectMissingCreatedTimestamp() {
            Map<String, String> headers = new HashMap<>();
            headers.put("Signature-Input", "sig-params=(\"@method\")");
            headers.put("Signature", "sig-params:dGVzdA==:");

            WebhookVerificationRequest request = new WebhookVerificationRequest(
                    "POST", "/webhooks/events", headers, "{}".getBytes(StandardCharsets.UTF_8)
            );

            WebhookVerificationResult result = verifier.verify(request);

            assertFalse(result.valid());
            assertTrue(result.errorMessage().contains("created"));
        }
    }
}
