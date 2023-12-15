package io.flowcore.integrations.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.flowcore.api.dto.ProviderCallContext;
import io.flowcore.api.dto.ProviderCallResult;
import io.flowcore.integrations.adapter.AbstractProviderAdapter;
import io.flowcore.integrations.http.ResilientHttpClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class KycAdapterContractTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .failOnUnmatchedRequests(true)
            .build();

    private TestKycAdapter adapter;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(1).waitDuration(Duration.ofMillis(100)).build());
        TimeLimiter tl = TimeLimiter.of(TimeLimiterConfig.ofDefaults());
        ResilientHttpClient httpClient = new ResilientHttpClient(restTemplate, cb, retry, tl);
        adapter = new TestKycAdapter(httpClient, new ObjectMapper());
    }

    @Test
    void verifyRequestIncludesContentTypeHeader() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/kyc/verify"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"APPROVED\",\"score\":95}")));

        ProviderCallContext ctx = new ProviderCallContext(
                "corr-" + UUID.randomUUID(), "demo.card.issuance",
                UUID.randomUUID(), "cardapp:u_001", Map.of(), Duration.ofSeconds(10), 1);

        adapter.execute(ctx, Map.of("userId", "u_001", "country", "DE"));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/kyc/verify"))
                .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void verifyRequestJsonPayload() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/kyc/verify"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"APPROVED\"}")));

        ProviderCallContext ctx = new ProviderCallContext(
                "corr-test", "demo.card.issuance", UUID.randomUUID(), "bk",
                Map.of(), Duration.ofSeconds(5), 1);

        adapter.execute(ctx, Map.of("userId", "u_test"));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/kyc/verify"))
                .withRequestBody(containing("userId"))
                .withRequestBody(containing("u_test")));
    }

    @Test
    void verifyReturnsSuccessOn200() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/kyc/verify"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"APPROVED\",\"score\":95}")));

        ProviderCallContext ctx = new ProviderCallContext(
                "corr-ok", "demo.card.issuance", UUID.randomUUID(), "bk",
                Map.of(), Duration.ofSeconds(5), 1);

        ProviderCallResult<Map<String, Object>> result =
                adapter.execute(ctx, Map.of("userId", "u_ok"));

        assertThat(result.status()).isEqualTo(ProviderCallResult.Status.SUCCESS);
    }

    static class TestKycAdapter extends AbstractProviderAdapter<Map<String, Object>, Map<String, Object>> {

        TestKycAdapter(ResilientHttpClient httpClient, ObjectMapper objectMapper) {
            super(httpClient, objectMapper, null);
        }

        @Override public String providerName() { return "mock-kyc"; }
        @Override public String operationName() { return "verify"; }
        @Override public Class<Map<String, Object>> requestType() { return (Class) Map.class; }
        @Override public Class<Map<String, Object>> responseType() { return (Class) Map.class; }
        @Override protected Class<Map<String, Object>> responseClass() { return (Class) Map.class; }
        @Override protected String baseUrl() { return wireMock.baseUrl() + "/api/v1/kyc/verify"; }
        @Override protected Map<String, String> authHeaders() { return Map.of(); }
    }
}
