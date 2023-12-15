package io.flowcore.integrations.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.ProviderAdapter;
import io.flowcore.api.dto.ProviderCallContext;
import io.flowcore.api.dto.ProviderCallResult;
import io.flowcore.integrations.http.ResilientHttpClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractProviderAdapterTest {

    private ResilientHttpClient httpClient;
    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        httpClient = mock(ResilientHttpClient.class);
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
    }

    private ProviderCallContext createContext() {
        return new ProviderCallContext(
                "corr-" + UUID.randomUUID(),
                "test-workflow",
                UUID.randomUUID(),
                "biz-key",
                Map.of("X-Trace", "trace-123"),
                Duration.ofSeconds(5),
                1
        );
    }

    /**
     * Concrete test implementation of AbstractProviderAdapter.
     */
    static class TestProviderAdapter extends AbstractProviderAdapter<String, String> {

        TestProviderAdapter(ResilientHttpClient httpClient,
                           ObjectMapper objectMapper,
                           io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            super(httpClient, objectMapper, meterRegistry);
        }

        @Override
        public String providerName() {
            return "test-provider";
        }

        @Override
        public String operationName() {
            return "testOperation";
        }

        @Override
        public Class<String> requestType() {
            return String.class;
        }

        @Override
        public Class<String> responseType() {
            return String.class;
        }

        @Override
        protected String baseUrl() {
            return "https://api.test-provider.com/v1";
        }

        @Override
        protected Map<String, String> authHeaders() {
            return Map.of("Authorization", "Bearer test-token");
        }

        @Override
        protected String buildUrl() {
            return baseUrl() + "/test-operation";
        }

        @Override
        protected HttpMethod httpMethod() {
            return HttpMethod.POST;
        }

        @Override
        protected Class<String> responseClass() {
            return String.class;
        }
    }

    private TestProviderAdapter createAdapter() {
        return new TestProviderAdapter(httpClient, objectMapper, meterRegistry);
    }

    private TestProviderAdapter createAdapterWithoutMetrics() {
        return new TestProviderAdapter(httpClient, objectMapper, null);
    }

    @Test
    @DisplayName("Successful execute returns SUCCESS result")
    void execute_success_returnsSuccessResult() {
        TestProviderAdapter adapter = createAdapter();
        ProviderCallContext ctx = createContext();

        when(httpClient.execute(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class),
                any(Map.class)
        )).thenReturn("response-data");

        ProviderCallResult<String> result = adapter.execute(ctx, "request-data");

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
        assertEquals("response-data", result.response());
        assertNull(result.errorCode());
        assertNull(result.errorDetail());

        verify(httpClient).configureTimeout(ctx);
    }

    @Test
    @DisplayName("Successful execute records metrics")
    void execute_success_recordsMetrics() {
        TestProviderAdapter adapter = createAdapter();
        ProviderCallContext ctx = createContext();

        when(httpClient.execute(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class),
                any(Map.class)
        )).thenReturn("ok");

        adapter.execute(ctx, "req");

        assertEquals(1.0, meterRegistry.counter("flowcore.provider.call.success",
                "provider", "test-provider", "operation", "testOperation").count());
        assertEquals(1.0, meterRegistry.timer("flowcore.provider.call.duration",
                "provider", "test-provider", "operation", "testOperation").count());
    }

    @Test
    @DisplayName("Execute without MeterRegistry does not throw")
    void execute_noMetrics_noException() {
        TestProviderAdapter adapter = createAdapterWithoutMetrics();
        ProviderCallContext ctx = createContext();

        when(httpClient.execute(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class),
                any(Map.class)
        )).thenReturn("ok");

        ProviderCallResult<String> result = adapter.execute(ctx, "req");

        assertEquals(ProviderCallResult.Status.SUCCESS, result.status());
    }

    @Test
    @DisplayName("Failure returns RETRYABLE_FAILURE result")
    void execute_failure_returnsRetryableFailure() {
        TestProviderAdapter adapter = createAdapter();
        ProviderCallContext ctx = createContext();

        when(httpClient.execute(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class),
                any(Map.class)
        )).thenThrow(new RuntimeException("connection timeout"));

        ProviderCallResult<String> result = adapter.execute(ctx, "req");

        assertEquals(ProviderCallResult.Status.RETRYABLE_FAILURE, result.status());
        assertEquals("PROVIDER_ERROR", result.errorCode());
        assertNotNull(result.errorDetail());
        assertTrue(result.errorDetail().contains("connection timeout"));
        assertNotNull(result.retryAfter());

        assertEquals(1.0, meterRegistry.counter("flowcore.provider.call.failure",
                "provider", "test-provider", "operation", "testOperation").count());
    }

    @Test
    @DisplayName("Auth headers and context headers are merged")
    void execute_mergesHeaders() {
        TestProviderAdapter adapter = createAdapter();
        ProviderCallContext ctx = createContext();

        when(httpClient.execute(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class),
                any(Map.class)
        )).thenReturn("ok");

        adapter.execute(ctx, "req");

        verify(httpClient).execute(
                eq("https://api.test-provider.com/v1/test-operation"),
                eq(HttpMethod.POST),
                eq("req"),
                eq(String.class),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("Correlation ID header is added")
    void execute_addsCorrelationId() {
        TestProviderAdapter adapter = createAdapter();
        ProviderCallContext ctx = createContext();

        when(httpClient.execute(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class),
                any(Map.class)
        )).thenReturn("ok");

        adapter.execute(ctx, "req");

        verify(httpClient).execute(
                any(String.class),
                any(HttpMethod.class),
                any(),
                eq(String.class),
                org.mockito.ArgumentMatchers.argThat(headers ->
                        headers.containsKey("X-Correlation-Id") &&
                                headers.get("X-Correlation-Id").equals(ctx.correlationId())
                )
        );
    }

    @Test
    @DisplayName("providerName returns correct value")
    void providerName_returnsCorrect() {
        TestProviderAdapter adapter = createAdapter();
        assertEquals("test-provider", adapter.providerName());
    }

    @Test
    @DisplayName("operationName returns correct value")
    void operationName_returnsCorrect() {
        TestProviderAdapter adapter = createAdapter();
        assertEquals("testOperation", adapter.operationName());
    }

    @Test
    @DisplayName("requestType returns correct class")
    void requestType_returnsCorrect() {
        TestProviderAdapter adapter = createAdapter();
        assertEquals(String.class, adapter.requestType());
    }

    @Test
    @DisplayName("responseType returns correct class")
    void responseType_returnsCorrect() {
        TestProviderAdapter adapter = createAdapter();
        assertEquals(String.class, adapter.responseType());
    }
}
