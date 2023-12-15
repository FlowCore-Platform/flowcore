package io.flowcore.tx.integration;

import io.flowcore.api.OutboxService;
import io.flowcore.api.dto.FailureInfo;
import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.api.dto.OutboxEventDraft;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest(classes = TransactionTestApplication.class)
@ActiveProfiles("test")
@Testcontainers
class OutboxPublisherIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("flowcore").withUsername("flowcore").withPassword("flowcore")
            .withInitScript("init-schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxService outboxService;

    @Test
    void publishesAndMarksPublished() {
        OutboxEventDraft draft = new OutboxEventDraft(
                "demo.card.issuance", "cardapp:u_001", "KYC_RESULT",
                "evt-" + UUID.randomUUID(), "{\"status\":\"APPROVED\"}", Map.of("traceId", "abc123"));
        UUID eventId = outboxService.enqueue(draft);
        assertThat(eventId).isNotNull();

        List<OutboxEvent> batch = outboxService.fetchDueBatch(10);
        assertThat(batch).hasSizeGreaterThanOrEqualTo(1);

        outboxService.markPublished(eventId, Instant.now());

        List<OutboxEvent> secondBatch = outboxService.fetchDueBatch(10);
        assertThat(secondBatch.stream().filter(e -> e.id().equals(eventId))).isEmpty();
    }

    @Test
    void eventKeyIsUnique() {
        String key = "unique-" + UUID.randomUUID();
        outboxService.enqueue(new OutboxEventDraft("test", "k1", "EVT", key, "{}", Map.of()));
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> outboxService.enqueue(
                        new OutboxEventDraft("test", "k1", "EVT", key, "{}", Map.of())));
    }
}
