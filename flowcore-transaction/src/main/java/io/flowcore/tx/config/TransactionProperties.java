package io.flowcore.tx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the FlowCore transaction module.
 */
@ConfigurationProperties(prefix = "flowcore.tx")
public record TransactionProperties(
    Idempotency idempotency,
    Outbox outbox,
    Timer timer
) {
    public TransactionProperties() {
        this(new Idempotency(), new Outbox(), new Timer());
    }

    public record Idempotency(
        int cleanupIntervalSeconds,
        int defaultExpirationMinutes
    ) {
        public Idempotency() {
            this(60, 1440);
        }
    }

    public record Outbox(
        int pollIntervalMs,
        int batchSize,
        int maxAttempts,
        long baseRetryDelayMs,
        long maxRetryDelayMs
    ) {
        public Outbox() {
            this(500, 50, 10, 1000, 60000);
        }
    }

    public record Timer(
        int pollIntervalMs,
        int batchSize
    ) {
        public Timer() {
            this(500, 100);
        }
    }
}
