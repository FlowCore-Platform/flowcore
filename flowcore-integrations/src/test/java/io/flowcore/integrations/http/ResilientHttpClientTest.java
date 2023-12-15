package io.flowcore.integrations.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.flowcore.api.dto.ProviderCallContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientHttpClientTest {

    private RestTemplate restTemplate;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private TimeLimiter timeLimiter;
    private ResilientHttpClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .minimumNumberOfCalls(3)
                .build();
        circuitBreaker = CircuitBreaker.of("test-cb", cbConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(attempt -> 50L)
                .build();
        retry = Retry.of("test-retry", retryConfig);

        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        timeLimiter = TimeLimiter.of("test-tl", tlConfig);

        client = new ResilientHttpClient(restTemplate, circuitBreaker, retry, timeLimiter);
    }

    @Test
    @DisplayName("Successful HTTP call returns response body")
    void execute_success_returnsResponseBody() {
        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("hello", HttpStatus.OK));

        String result = client.execute(
                "http://localhost/test",
                HttpMethod.GET,
                null,
                String.class,
                Map.of("X-Auth", "token123")
        );

        assertEquals("hello", result);
        verify(restTemplate).exchange(
                eq("http://localhost/test"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        );
    }

    @Test
    @DisplayName("Successful call with null headers")
    void execute_success_nullHeaders() {
        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String result = client.execute(
                "http://localhost/test",
                HttpMethod.GET,
                null,
                String.class,
                null
        );

        assertEquals("ok", result);
    }

    @Test
    @DisplayName("Successful call with request body")
    void execute_success_withBody() {
        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("created", HttpStatus.CREATED));

        String result = client.execute(
                "http://localhost/test",
                HttpMethod.POST,
                "request-body",
                String.class,
                Map.of()
        );

        assertEquals("created", result);
    }

    @Test
    @DisplayName("Retry on transient failure then success")
    void execute_retryOnFailure_thenSuccess() {
        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class)
        ))
                .thenThrow(new RestClientException("connection refused"))
                .thenReturn(new ResponseEntity<>("recovered", HttpStatus.OK));

        String result = client.execute(
                "http://localhost/test",
                HttpMethod.GET,
                null,
                String.class,
                null
        );

        assertEquals("recovered", result);
        verify(restTemplate, times(2)).exchange(
                eq("http://localhost/test"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        );
    }

    @Test
    @DisplayName("All retries exhausted throws RestClientException")
    void execute_allRetriesExhausted_throwsException() {
        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class)
        )).thenThrow(new RestClientException("persistent failure"));

        RestClientException exception = assertThrows(RestClientException.class, () ->
                client.execute(
                        "http://localhost/test",
                        HttpMethod.GET,
                        null,
                        String.class,
                        null
                )
        );

        assertNotNull(exception);
        verify(restTemplate, times(3)).exchange(
                eq("http://localhost/test"),
                eq(HttpMethod.GET),
                any(),
                eq(String.class)
        );
    }

    @Test
    @DisplayName("Circuit breaker opens after reaching failure threshold")
    void execute_circuitBreakerOpens_afterFailures() {
        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class)
        )).thenThrow(new RestClientException("failure"));

        // Exhaust retries and fill the sliding window to trigger the circuit breaker
        for (int i = 0; i < 10; i++) {
            try {
                client.execute("http://localhost/test", HttpMethod.GET,
                        null, String.class, null);
            } catch (Exception ignored) {
                // expected
            }
        }

        // Circuit breaker should be open now
        assertThrows(CallNotPermittedException.class, () ->
                client.execute(
                        "http://localhost/test",
                        HttpMethod.GET,
                        null,
                        String.class,
                        null
                )
        );
    }

    @Test
    @DisplayName("configureTimeout updates time limiter from context")
    void configureTimeout_updatesTimeLimiter() {
        ProviderCallContext context = new ProviderCallContext(
                "corr-123",
                "test-workflow",
                UUID.randomUUID(),
                "biz-key",
                Map.of(),
                Duration.ofSeconds(10),
                1
        );

        client.configureTimeout(context);

        assertEquals(Duration.ofSeconds(10),
                client.getActiveTimeLimiter().getTimeLimiterConfig().getTimeoutDuration());
    }

    @Test
    @DisplayName("configureTimeout with null context does not throw")
    void configureTimeout_nullContext_noException() {
        client.configureTimeout(null);
        // no exception thrown
    }

    @Test
    @DisplayName("configureTimeout with null deadline does not throw")
    void configureTimeout_nullDeadline_noException() {
        ProviderCallContext context = new ProviderCallContext(
                "corr-123",
                "test-workflow",
                UUID.randomUUID(),
                "biz-key",
                Map.of(),
                null,
                1
        );

        client.configureTimeout(context);
        // no exception thrown
    }
}
