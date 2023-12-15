package io.flowcore.security.webhook;

import io.flowcore.api.WebhookVerifier;
import io.flowcore.api.dto.WebhookVerificationRequest;
import io.flowcore.api.dto.WebhookVerificationResult;
import io.flowcore.security.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link WebhookVerifier} that verifies webhook signatures
 * using HMAC-SHA256 following the RFC 9421 style signature input format.
 * <p>
 * Verification steps:
 * <ol>
 *     <li>Extract {@code Signature-Input} and {@code Signature} headers</li>
 *     <li>Reconstruct the signature base from method, path, headers, and content-digest</li>
 *     <li>Verify the HMAC-SHA256 signature against the shared secret</li>
 *     <li>Check timestamp freshness within the configured max skew</li>
 * </ol>
 */
public class DefaultWebhookVerifier implements WebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebhookVerifier.class);

    private static final String SIGNATURE_INPUT_HEADER = "Signature-Input";
    private static final String SIGNATURE_HEADER = "Signature";
    private static final String CONTENT_DIGEST_HEADER = "Content-Digest";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";

    private static final Pattern CREATED_PATTERN = Pattern.compile("created=(\\d+)");
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("keyid=\"([^\"]+)\"");

    private final SecurityProperties properties;
    private final String sharedSecret;

    /**
     * Constructs a new {@code DefaultWebhookVerifier}.
     *
     * @param properties   the security configuration properties
     * @param sharedSecret the shared secret for HMAC-SHA256 verification
     */
    public DefaultWebhookVerifier(SecurityProperties properties, String sharedSecret) {
        this.properties = properties;
        this.sharedSecret = sharedSecret;
    }

    @Override
    public WebhookVerificationResult verify(WebhookVerificationRequest request) {
        Map<String, String> headers = request.headers();

        if (headers == null || headers.isEmpty()) {
            return WebhookVerificationResult.invalid("No headers provided");
        }

        String signatureInput = getHeader(headers, SIGNATURE_INPUT_HEADER);
        String signature = getHeader(headers, SIGNATURE_HEADER);

        if (signatureInput == null || signatureInput.isBlank()) {
            return WebhookVerificationResult.invalid("Missing Signature-Input header");
        }

        if (signature == null || signature.isBlank()) {
            return WebhookVerificationResult.invalid("Missing Signature header");
        }

        // Check timestamp freshness
        Instant created = extractCreated(signatureInput);
        if (created == null) {
            return WebhookVerificationResult.invalid("Missing created timestamp in Signature-Input");
        }

        int maxSkew = properties.getWebhookMaxSkewSeconds();
        long skewSeconds = Math.abs(Instant.now().getEpochSecond() - created.getEpochSecond());
        if (skewSeconds > maxSkew) {
            log.debug("Webhook timestamp skew {}s exceeds max {}s", skewSeconds, maxSkew);
            return WebhookVerificationResult.invalid(
                    "Timestamp skew exceeds maximum allowed (" + skewSeconds + "s > " + maxSkew + "s)");
        }

        // Reconstruct signature base
        String signatureBase = buildSignatureBase(request, signatureInput);

        // Verify HMAC-SHA256 signature
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] expectedBytes = mac.doFinal(signatureBase.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(expectedBytes);

            // Extract the signature value (strip label prefix if present)
            String actualSignature = extractSignatureValue(signature);

            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    actualSignature.getBytes(StandardCharsets.UTF_8))) {
                log.debug("Webhook signature mismatch");
                return WebhookVerificationResult.invalid("Signature verification failed");
            }
        } catch (Exception ex) {
            log.debug("Webhook signature verification error: {}", ex.getMessage());
            return WebhookVerificationResult.invalid("Signature verification error: " + ex.getMessage());
        }

        return WebhookVerificationResult.success();
    }

    private String buildSignatureBase(WebhookVerificationRequest request,
                                      String signatureInput) {
        StringBuilder sb = new StringBuilder();

        // Parse the covered headers from Signature-Input
        // Format: sig-params=("@method" "@path" "content-digest");created=1234567890;keyid="secret"
        String headerListPart = signatureInput;
        int semiIndex = signatureInput.indexOf(";created");
        if (semiIndex > 0) {
            headerListPart = signatureInput.substring(0, semiIndex);
        }

        // Extract label and header list
        // e.g. sig-params=("@method" "@path" "content-digest")
        int eqIndex = headerListPart.indexOf('=');
        if (eqIndex < 0) {
            // No label, treat entire thing as header list
            return headerListPart;
        }

        String headerList = headerListPart.substring(eqIndex + 1).trim();

        // Parse individual header names from the list
        // Remove outer parens, then split by spaces
        if (headerList.startsWith("(") && headerList.endsWith(")")) {
            headerList = headerList.substring(1, headerList.length() - 1);
        }

        String[] headerNames = headerList.split("\\s+");

        for (String headerName : headerNames) {
            // Remove surrounding quotes
            String cleanName = headerName.replace("\"", "");

            if ("@method".equalsIgnoreCase(cleanName)) {
                String method = request.method() != null ? request.method() : "POST";
                sb.append("\"@method\": ").append(method).append('\n');
            } else if ("@path".equalsIgnoreCase(cleanName)) {
                String path = request.path() != null ? request.path() : "/";
                sb.append("\"@path\": ").append(path).append('\n');
            } else {
                // Regular header - look it up from request headers
                String value = request.headers() != null
                        ? request.headers().getOrDefault(cleanName.toLowerCase(), "")
                        : "";
                sb.append("\"").append(cleanName.toLowerCase()).append("\": ").append(value).append('\n');
            }
        }

        // Append the signature-params line
        String label = headerListPart.substring(0, eqIndex).trim();
        String paramsSuffix = signatureInput.substring(semiIndex > 0 ? semiIndex + 1 : headerListPart.length());
        sb.append("\"@signature-params\": ").append(label).append(";").append(paramsSuffix);

        return sb.toString();
    }

    private Instant extractCreated(String signatureInput) {
        Matcher matcher = CREATED_PATTERN.matcher(signatureInput);
        if (matcher.find()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String extractSignatureValue(String signature) {
        // Format: label=:base64value:
        int colonIndex = signature.indexOf(':');
        if (colonIndex >= 0) {
            String afterLabel = signature.substring(colonIndex + 1);
            // Remove trailing colon if present
            if (afterLabel.endsWith(":")) {
                afterLabel = afterLabel.substring(0, afterLabel.length() - 1);
            }
            return afterLabel.trim();
        }
        return signature.trim();
    }

    private static String getHeader(Map<String, String> headers, String name) {
        // Case-insensitive header lookup
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
