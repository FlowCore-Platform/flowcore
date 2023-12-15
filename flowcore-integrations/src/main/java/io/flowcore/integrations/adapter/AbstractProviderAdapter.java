package io.flowcore.integrations.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.ProviderAdapter;
import io.flowcore.api.dto.ProviderCallContext;
import io.flowcore.api.dto.ProviderCallResult;
import io.flowcore.integrations.http.ResilientHttpClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for provider adapters.
 * <p>
 * Subclasses provide provider-specific details:
 * <ul>
 *   <li>{@link #baseUrl()} - the provider base URL</li>
 *   <li>{@link #authHeaders()} - authentication headers</li>
 * </ul>
 * <p>
 * This template method handles resilience, metrics instrumentation,
 * and result mapping automatically.
 *
 * @param <RequestT>  the request type
 * @param <ResponseT> the response type
 */
public abstract class AbstractProviderAdapter<RequestT, ResponseT>
        implements ProviderAdapter<RequestT, ResponseT> {

    private static final Logger log = LoggerFactory.getLogger(AbstractProviderAdapter.class);

    protected final ResilientHttpClient httpClient;
    protected final ObjectMapper objectMapper;

    private final Timer callTimer;
    private final Counter successCounter;
    private final Counter failureCounter;

    /**
     * Constructs an AbstractProviderAdapter with required dependencies.
     *
     * @param httpClient   the resilient HTTP client for outbound calls
     * @param objectMapper the JSON object mapper for serialization
     * @param meterRegistry the Micrometer registry for metrics (may be null)
     */
    protected AbstractProviderAdapter(
            ResilientHttpClient httpClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;

        if (meterRegistry != null) {
            String providerName = providerName();
            String operationName = operationName();
            this.callTimer = Timer.builder("flowcore.provider.call.duration")
                    .tag("provider", providerName)
                    .tag("operation", operationName)
                    .register(meterRegistry);
            this.successCounter = Counter.builder("flowcore.provider.call.success")
                    .tag("provider", providerName)
                    .tag("operation", operationName)
                    .register(meterRegistry);
            this.failureCounter = Counter.builder("flowcore.provider.call.failure")
                    .tag("provider", providerName)
                    .tag("operation", operationName)
                    .register(meterRegistry);
        } else {
            this.callTimer = null;
            this.successCounter = null;
            this.failureCounter = null;
        }
    }

    /**
     * @return the base URL for the external provider API
     */
    protected abstract String baseUrl();

    /**
     * @return the authentication headers required by the provider
     */
    protected abstract Map<String, String> authHeaders();

    /**
     * Builds the full URL for the API call.
     * <p>
     * Default implementation concatenates {@link #baseUrl()} with the operation path.
     * Subclasses may override for custom URL construction.
     *
     * @return the full request URL
     */
    protected String buildUrl() {
        return baseUrl();
    }

    /**
     * @return the HTTP method for the API call (default: POST)
     */
    protected HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    /**
     * @return the response class for deserialization
     */
    protected abstract Class<ResponseT> responseClass();

    @Override
    public ProviderCallResult<ResponseT> execute(ProviderCallContext ctx, RequestT request) {
        httpClient.configureTimeout(ctx);

        Map<String, String> mergedHeaders = new HashMap<>(authHeaders());
        if (ctx.headers() != null) {
            mergedHeaders.putAll(ctx.headers());
        }
        mergedHeaders.put("X-Correlation-Id", ctx.correlationId());

        String url = buildUrl();
        HttpMethod method = httpMethod();
        Class<ResponseT> responseType = responseClass();

        long startNanos = System.nanoTime();
        try {
            log.debug("[{}] Executing {} {} (attempt={})",
                    ctx.correlationId(), method, url, ctx.attempt());

            ResponseT response = httpClient.execute(
                    url, method, request, responseType, mergedHeaders
            );

            recordSuccess(startNanos);
            log.debug("[{}] Call completed successfully", ctx.correlationId());
            return ProviderCallResult.success(response);

        } catch (Exception ex) {
            recordFailure(startNanos);
            log.warn("[{}] Call failed: {}", ctx.correlationId(), ex.getMessage());

            if (isRetryable(ex)) {
                return ProviderCallResult.retryableFailure(
                        "PROVIDER_ERROR",
                        ex.getMessage(),
                        Duration.ofSeconds(1)
                );
            }
            return ProviderCallResult.fatalFailure(
                    "PROVIDER_FATAL",
                    ex.getMessage()
            );
        }
    }

    /**
     * Determines if an exception represents a retryable error.
     * <p>
     * Default implementation considers network errors and 5xx responses as retryable.
     * Subclasses may override for provider-specific retry logic.
     *
     * @param ex the exception to evaluate
     * @return true if the call can be retried
     */
    protected boolean isRetryable(Exception ex) {
        return true;
    }

    private void recordSuccess(long startNanos) {
        if (callTimer != null) {
            callTimer.record(Duration.ofNanos(System.nanoTime() - startNanos));
        }
        if (successCounter != null) {
            successCounter.increment();
        }
    }

    private void recordFailure(long startNanos) {
        if (callTimer != null) {
            callTimer.record(Duration.ofNanos(System.nanoTime() - startNanos));
        }
        if (failureCounter != null) {
            failureCounter.increment();
        }
    }
}
