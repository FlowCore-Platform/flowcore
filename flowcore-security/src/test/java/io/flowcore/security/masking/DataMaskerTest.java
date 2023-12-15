package io.flowcore.security.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMaskerTest {

    @Nested
    @DisplayName("maskPan")
    class MaskPanTests {

        @Test
        @DisplayName("should mask middle digits keeping first 6 and last 4")
        void shouldMaskMiddleDigits() {
            String result = DataMasker.maskPan("4111111111111111");
            assertEquals("411111******1111", result);
        }

        @Test
        @DisplayName("should handle 10-digit PAN")
        void shouldHandleTenDigitPan() {
            String result = DataMasker.maskPan("1234567890");
            assertEquals("1234567890", result);
        }

        @Test
        @DisplayName("should return *** for PAN shorter than 10 digits")
        void shouldReturnMaskForShortPan() {
            assertEquals("***", DataMasker.maskPan("123456789"));
        }

        @Test
        @DisplayName("should return *** for null PAN")
        void shouldReturnMaskForNullPan() {
            assertEquals("***", DataMasker.maskPan(null));
        }

        @Test
        @DisplayName("should handle PAN with dashes")
        void shouldHandlePanWithDashes() {
            String result = DataMasker.maskPan("4111-1111-1111-1111");
            // maskPan masks by position (chars 6..n-4), not by digit logic
            assertEquals("4111-1*********1111", result);
        }
    }

    @Nested
    @DisplayName("maskToken")
    class MaskTokenTests {

        @Test
        @DisplayName("should always return ***")
        void shouldReturnMask() {
            assertEquals("***", DataMasker.maskToken("sk_live_abc123def456"));
        }

        @Test
        @DisplayName("should return *** for null")
        void shouldReturnMaskForNull() {
            assertEquals("***", DataMasker.maskToken(null));
        }
    }

    @Nested
    @DisplayName("maskEmail")
    class MaskEmailTests {

        @Test
        @DisplayName("should mask middle of local part keeping first 2 chars and domain")
        void shouldMaskEmailLocalPart() {
            assertEquals("jo***@example.com", DataMasker.maskEmail("john.doe@example.com"));
        }

        @Test
        @DisplayName("should keep short local part as-is")
        void shouldKeepShortLocalPart() {
            assertEquals("ab@example.com", DataMasker.maskEmail("ab@example.com"));
        }

        @Test
        @DisplayName("should handle single char local part")
        void shouldHandleSingleCharLocalPart() {
            assertEquals("a@example.com", DataMasker.maskEmail("a@example.com"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"invalid-email", "no-at-sign"})
        @DisplayName("should return *** for invalid emails")
        void shouldReturnMaskForInvalidEmails(String email) {
            assertEquals("***", DataMasker.maskEmail(email));
        }
    }

    @Nested
    @DisplayName("maskName")
    class MaskNameTests {

        @Test
        @DisplayName("should keep first char and mask the rest")
        void shouldKeepFirstChar() {
            assertEquals("J***", DataMasker.maskName("John"));
        }

        @Test
        @DisplayName("should return single char as-is")
        void shouldReturnSingleCharAsIs() {
            assertEquals("J", DataMasker.maskName("J"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("should return *** for null or blank names")
        void shouldReturnMaskForBlankNames(String name) {
            assertEquals("***", DataMasker.maskName(name));
        }
    }

    @Nested
    @DisplayName("maskAll")
    class MaskAllTests {

        @Test
        @DisplayName("should always return ***")
        void shouldReturnMask() {
            assertEquals("***", DataMasker.maskAll("any sensitive value"));
        }

        @Test
        @DisplayName("should return *** for null")
        void shouldReturnMaskForNull() {
            assertEquals("***", DataMasker.maskAll(null));
        }
    }

    @Nested
    @DisplayName("maskJson")
    class MaskJsonTests {

        @Test
        @DisplayName("should mask specified top-level fields")
        void shouldMaskTopLevelFields() {
            String json = """
                    {"name": "John", "email": "john@test.com", "public_field": "visible"}""";

            String result = DataMasker.maskJson(json, Set.of("name", "email"));

            assertEquals(
                    "{\"name\":\"***\",\"email\":\"***\",\"public_field\":\"visible\"}",
                    result);
        }

        @Test
        @DisplayName("should mask nested fields using dot notation")
        void shouldMaskNestedFields() {
            String json = """
                    {"user": {"name": "John", "age": 30}}""";

            String result = DataMasker.maskJson(json, Set.of("user.name"));

            assertEquals(
                    "{\"user\":{\"name\":\"***\",\"age\":30}}",
                    result);
        }

        @Test
        @DisplayName("should return original JSON when no sensitive fields specified")
        void shouldReturnOriginalWhenNoFields() {
            String json = "{\"name\": \"John\"}";

            String result = DataMasker.maskJson(json, Set.of());

            assertEquals(json, result);
        }

        @Test
        @DisplayName("should return null when input is null")
        void shouldReturnNullForNullInput() {
            assertEquals(null, DataMasker.maskJson(null, Set.of("name")));
        }

        @Test
        @DisplayName("should return empty string when input is empty")
        void shouldReturnEmptyForEmptyInput() {
            assertEquals("", DataMasker.maskJson("", Set.of("name")));
        }

        @Test
        @DisplayName("should return original string when JSON is malformed")
        void shouldReturnOriginalForMalformedJson() {
            String malformed = "{not valid json}";

            String result = DataMasker.maskJson(malformed, Set.of("name"));

            assertEquals(malformed, result);
        }

        @Test
        @DisplayName("should handle null sensitive fields set")
        void shouldHandleNullSensitiveFields() {
            String json = "{\"name\": \"John\"}";

            String result = DataMasker.maskJson(json, null);

            assertEquals(json, result);
        }

        @Test
        @DisplayName("should mask multiple fields in complex JSON")
        void shouldMaskMultipleFields() {
            String json = """
                    {"pan": "4111111111111111", "cvv": "123", "amount": 100.50, "currency": "USD"}""";

            String result = DataMasker.maskJson(json, Set.of("pan", "cvv"));

            // Jackson serializes 100.50 as 100.5
            assertTrue(result.contains("\"pan\":\"***\""));
            assertTrue(result.contains("\"cvv\":\"***\""));
            assertTrue(result.contains("\"amount\""));
            assertTrue(result.contains("\"currency\":\"USD\""));
        }
    }
}
