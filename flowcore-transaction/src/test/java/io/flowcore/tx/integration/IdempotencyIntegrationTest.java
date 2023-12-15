package io.flowcore.tx.integration;

import io.flowcore.api.IdempotencyService;
import io.flowcore.api.dto.StoredResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TransactionTestApplication.class)
@ActiveProfiles("test")
@Testcontainers
class IdempotencyIntegrationTest {

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
    private IdempotencyService idempotencyService;

    @Test
    void storesAndFindsResponse() {
        String key = "idem-" + UUID.randomUUID();
        assertThat(idempotencyService.find("scope", key, "h1")).isEmpty();
        idempotencyService.store("scope", key, "h1", "{\"ok\":true}", Instant.now().plusSeconds(3600));
        Optional<StoredResponse> found = idempotencyService.find("scope", key, "h1");
        assertThat(found).isPresent();
        assertThat(found.get().responseJson()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void differentScopesAreIndependent() {
        String key = "shared-" + UUID.randomUUID();
        idempotencyService.store("scope-a", key, "h", "{}", Instant.now().plusSeconds(3600));
        assertThat(idempotencyService.find("scope-b", key, "h")).isEmpty();
        assertThat(idempotencyService.find("scope-a", key, "h")).isPresent();
    }
}
