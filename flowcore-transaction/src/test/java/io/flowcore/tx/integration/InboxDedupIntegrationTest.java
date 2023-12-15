package io.flowcore.tx.integration;

import io.flowcore.api.InboxService;
import io.flowcore.api.dto.InboxAcceptResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TransactionTestApplication.class)
@ActiveProfiles("test")
@Testcontainers
class InboxDedupIntegrationTest {

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
    private InboxService inboxService;

    @Test
    void duplicateMessageNoSideEffects() {
        String msgId = "msg-" + UUID.randomUUID();
        assertThat(inboxService.tryAccept("kafka", msgId, "engine").accepted()).isTrue();
        assertThat(inboxService.tryAccept("kafka", msgId, "engine").accepted()).isFalse();
        assertThat(inboxService.tryAccept("kafka", msgId, "engine").accepted()).isFalse();
    }

    @Test
    void differentSourceIsNotDuplicate() {
        String msgId = "msg-" + UUID.randomUUID();
        assertThat(inboxService.tryAccept("kafka", msgId, "ga").accepted()).isTrue();
        assertThat(inboxService.tryAccept("webhook", msgId, "ga").accepted()).isTrue();
        assertThat(inboxService.tryAccept("kafka", msgId, "gb").accepted()).isTrue();
    }
}
