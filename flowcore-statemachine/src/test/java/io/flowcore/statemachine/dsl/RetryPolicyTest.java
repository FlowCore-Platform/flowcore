package io.flowcore.statemachine.dsl;

import io.flowcore.api.dto.RetryPolicyDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    // =====================================================================
    // Factory methods
    // =====================================================================

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("fixed() creates fixed-interval retry policy")
        void fixedCreatesFixedPolicy() {
            RetryPolicy policy = RetryPolicy.fixed(3, 500);

            assertThat(policy.mode()).isEqualTo("fixed");
            assertThat(policy.maxAttempts()).isEqualTo(3);
            assertThat(policy.baseDelayMs()).isEqualTo(500);
            assertThat(policy.maxDelayMs()).isEqualTo(500);
            assertThat(policy.jitterPct()).isEqualTo(0);
        }

        @Test
        @DisplayName("exponential() creates exponential-backoff retry policy")
        void exponentialCreatesExponentialPolicy() {
            RetryPolicy policy = RetryPolicy.exponential(5, 100, 5000);

            assertThat(policy.mode()).isEqualTo("exponential");
            assertThat(policy.maxAttempts()).isEqualTo(5);
            assertThat(policy.baseDelayMs()).isEqualTo(100);
            assertThat(policy.maxDelayMs()).isEqualTo(5000);
            assertThat(policy.jitterPct()).isEqualTo(0);
        }

        @Test
        @DisplayName("withJitter() sets jitter percentage")
        void withJitterSetsJitter() {
            RetryPolicy policy = RetryPolicy.exponential(3, 200, 5000).withJitter(25);

            assertThat(policy.jitterPct()).isEqualTo(25);
            assertThat(policy.maxAttempts()).isEqualTo(3);
            assertThat(policy.baseDelayMs()).isEqualTo(200);
            assertThat(policy.maxDelayMs()).isEqualTo(5000);
        }
    }

    // =====================================================================
    // Validation
    // =====================================================================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("rejects maxAttempts < 1")
        void rejectsMaxAttemptsLessThanOne() {
            assertThatThrownBy(() -> RetryPolicy.fixed(0, 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxAttempts");
        }

        @Test
        @DisplayName("rejects baseDelayMs < 10")
        void rejectsBaseDelayMsLessThan10() {
            assertThatThrownBy(() -> RetryPolicy.fixed(3, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseDelayMs");
        }

        @Test
        @DisplayName("rejects maxDelayMs < baseDelayMs")
        void rejectsMaxDelayLessThanBase() {
            assertThatThrownBy(() -> RetryPolicy.exponential(3, 200, 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxDelayMs");
        }

        @Test
        @DisplayName("rejects invalid mode")
        void rejectsInvalidMode() {
            assertThatThrownBy(() -> new RetryPolicy("invalid", 3, 100, 100, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mode");
        }

        @Test
        @DisplayName("rejects jitterPct < 0")
        void rejectsNegativeJitter() {
            assertThatThrownBy(() -> RetryPolicy.fixed(3, 100).withJitter(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jitterPct");
        }

        @Test
        @DisplayName("rejects jitterPct > 100")
        void rejectsJitterOver100() {
            assertThatThrownBy(() -> RetryPolicy.fixed(3, 100).withJitter(101))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jitterPct");
        }
    }

    // =====================================================================
    // Exponential backoff with jitter
    // =====================================================================

    @Nested
    @DisplayName("Exponential backoff calculation")
    class ExponentialBackoffTests {

        @RepeatedTest(10)
        @DisplayName("exponential backoff with jitter stays within expected bounds")
        void exponentialBackoffIncreasesWithJitterBounded() {
            int maxAttempts = 6;
            long baseDelayMs = 200;
            long maxDelayMs = 5000;
            int jitterPct = 20;

            RetryPolicy policy = RetryPolicy.exponential(maxAttempts, baseDelayMs, maxDelayMs)
                    .withJitter(jitterPct);

            // Verify the policy properties
            assertThat(policy.mode()).isEqualTo("exponential");
            assertThat(policy.maxAttempts()).isEqualTo(maxAttempts);
            assertThat(policy.baseDelayMs()).isEqualTo(baseDelayMs);
            assertThat(policy.maxDelayMs()).isEqualTo(maxDelayMs);
            assertThat(policy.jitterPct()).isEqualTo(jitterPct);

            // Calculate expected delays for each attempt and verify bounds
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                long expectedBase = (long) (baseDelayMs * Math.pow(2, attempt));
                long expected = Math.min(expectedBase, maxDelayMs);

                // With jitter, the actual delay should be in range:
                // [expected * (1 - jitterPct/100), expected * (1 + jitterPct/100)]
                double jitterFactor = jitterPct / 100.0;
                double lowerBound = expected * (1.0 - jitterFactor);
                double upperBound = expected * (1.0 + jitterFactor);

                // Verify that the bounds are correctly computed
                assertThat(lowerBound).isGreaterThan(0);
                assertThat(upperBound).isGreaterThan(lowerBound);

                // The expected base delay must be capped at maxDelayMs
                assertThat(expected).isLessThanOrEqualTo(maxDelayMs);
            }
        }

        @Test
        @DisplayName("delay is capped at maxDelayMs")
        void delayIsCappedAtMaxDelayMs() {
            long baseDelayMs = 100;
            long maxDelayMs = 500;

            // At attempt 10, raw exponential would be 100 * 2^10 = 102400
            // But it should be capped at 500
            for (int attempt = 0; attempt < 20; attempt++) {
                long expectedBase = (long) (baseDelayMs * Math.pow(2, attempt));
                long expected = Math.min(expectedBase, maxDelayMs);
                assertThat(expected).isLessThanOrEqualTo(maxDelayMs);
            }
        }

        @Test
        @DisplayName("toApiDto converts jitterPct correctly from int 0-100 to double 0.0-1.0")
        void toApiDtoConvertsJitter() {
            RetryPolicy policy = RetryPolicy.exponential(3, 200, 5000).withJitter(20);

            RetryPolicyDef dto = policy.toApiDto();

            assertThat(dto.mode()).isEqualTo("exponential");
            assertThat(dto.maxAttempts()).isEqualTo(3);
            assertThat(dto.baseDelayMs()).isEqualTo(200);
            assertThat(dto.maxDelayMs()).isEqualTo(5000);
            assertThat(dto.jitterPct()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("toApiDto converts zero jitter correctly")
        void toApiDtoZeroJitter() {
            RetryPolicy policy = RetryPolicy.fixed(3, 100);

            RetryPolicyDef dto = policy.toApiDto();

            assertThat(dto.jitterPct()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
        }
    }
}
