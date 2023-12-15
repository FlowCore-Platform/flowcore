package io.flowcore.demo.card.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for card issuance demo running with Testcontainers (Postgres).
 * Verifies the full HTTP request → controller → engine flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CardIssuanceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("flowcore")
            .withUsername("flowcore")
            .withPassword("flowcore");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void issueCardReturns202() {
        String idempotencyKey = "integration-test-" + UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        Map<String, Object> body = Map.of(
                "userId", "u_integration",
                "tenant", "demo",
                "cardProduct", "VISA_VIRTUAL",
                "country", "DE",
                "wallet", Map.of("applePay", true, "googlePay", true)
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/cards/issue", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("workflowInstanceId")).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("RUNNING");
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameResponse() {
        String idempotencyKey = "dup-test-" + UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        Map<String, Object> body = Map.of(
                "userId", "u_dup",
                "tenant", "demo",
                "cardProduct", "VISA_VIRTUAL",
                "country", "DE"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> first = restTemplate.postForEntity(
                "/api/v1/cards/issue", request, Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity(
                "/api/v1/cards/issue", request, Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(second.getBody().get("workflowInstanceId"))
                .isEqualTo(first.getBody().get("workflowInstanceId"));
    }

    @Test
    void getApplicationStatusReturns404ForUnknown() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/cards/applications/" + UUID.randomUUID(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
