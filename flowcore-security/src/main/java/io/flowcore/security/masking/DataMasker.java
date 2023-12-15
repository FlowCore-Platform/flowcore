package io.flowcore.security.masking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Set;

/**
 * Utility class for masking sensitive data such as PANs, tokens, emails, and names.
 * <p>
 * All methods are static and stateless. Thread-safe by design.
 */
public final class DataMasker {

    private static final String MASK_ALL = "***";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DataMasker() {
        // prevent instantiation
    }

    /**
     * Masks a Primary Account Number (PAN), keeping the first 6 and last 4 digits.
     * Non-digit characters are preserved in their original positions.
     *
     * @param pan the PAN to mask
     * @return the masked PAN, or {@code "***"} if the input is too short
     */
    public static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return MASK_ALL;
        }
        StringBuilder sb = new StringBuilder(pan.length());
        for (int i = 0; i < pan.length(); i++) {
            char c = pan.charAt(i);
            if (i < 6 || i >= pan.length() - 4) {
                sb.append(c);
            } else {
                sb.append('*');
            }
        }
        return sb.toString();
    }

    /**
     * Masks a token entirely, replacing it with {@code "***"}.
     *
     * @param token the token to mask
     * @return {@code "***"}
     */
    public static String maskToken(String token) {
        return MASK_ALL;
    }

    /**
     * Masks an email address, keeping the first 2 characters of the local part
     * and the full domain.
     *
     * @param email the email to mask
     * @return the masked email, or {@code "***"} if the format is invalid
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return MASK_ALL;
        }
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 2) {
            return localPart + domain;
        }

        return localPart.substring(0, 2) + "***" + domain;
    }

    /**
     * Masks a name, keeping only the first character visible.
     *
     * @param name the name to mask
     * @return the masked name, or {@code "***"} if the input is too short
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return MASK_ALL;
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "***";
    }

    /**
     * Replaces the entire value with {@code "***"}.
     *
     * @param value the value to mask
     * @return {@code "***"}
     */
    public static String maskAll(String value) {
        return MASK_ALL;
    }

    /**
     * Masks specified fields in a JSON string, replacing their values with {@code "***"}.
     * <p>
     * Field matching is case-sensitive. Nested fields are supported using dot notation
     * (e.g., {@code "user.email"}).
     *
     * @param json            the JSON string to process
     * @param sensitiveFields the set of field names to mask
     * @return the JSON string with sensitive fields masked
     */
    public static String maskJson(String json, Set<String> sensitiveFields) {
        if (json == null || json.isBlank()) {
            return json;
        }
        if (sensitiveFields == null || sensitiveFields.isEmpty()) {
            return json;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            maskNode(root, sensitiveFields, "");
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            // If we cannot parse the JSON, return as-is
            return json;
        }
    }

    private static void maskNode(JsonNode node, Set<String> sensitiveFields, String path) {
        if (!node.isObject()) {
            return;
        }

        ObjectNode objectNode = (ObjectNode) node;
        var iterator = objectNode.fields();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String fieldPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();

            if (sensitiveFields.contains(entry.getKey()) || sensitiveFields.contains(fieldPath)) {
                objectNode.set(entry.getKey(), TextNode.valueOf(MASK_ALL));
            } else if (entry.getValue().isObject()) {
                maskNode(entry.getValue(), sensitiveFields, fieldPath);
            }
        }
    }
}
