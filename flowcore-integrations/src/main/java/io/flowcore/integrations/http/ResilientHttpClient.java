package io.flowcore.integrations.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.flowcore.api.dto.ProviderCallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP client wrapper with Resilience4j circuit breaker, retry, and time limiter.
 * <p>
 * Configuration defaults:
 * <ul>
 *   <li>Circuit breaker: 50-call sliding window, 50% failure threshold, 30s wait in open state</li>
 *   <li>Retry: max 3 attempts, exponential backoff 200ms to 2s</li>
 *   <li>Time limiter: per-call timeout derived from {@link ProviderCallContext#deadline()}</li>
 * </ul>
 */
@Component
public class ResilientHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientHttpClient.class);

    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TimeLimiter defaultTimeLimiter;
    private final ExecutorService executorService;

    private volatile TimeLimiter activeTimeLimiter;

    /**
     * Constructs a ResilientHttpClient with the given dependencies.
     *
     * @param restTemplate        the underlying REST template for HTTP calls
     * @param circuitBreaker      the circuit breaker instance
     * @param retry               the retry instance
     * @param defaultTimeLimiter  the default time limiter instance
     */
    public ResilientHttpClient(
            RestTemplate restTemplate,
            CircuitBreaker circuitBreaker,
            Retry retry,
            TimeLimiter defaultTimeLimiter
    ) {
        this.restTemplate = restTemplate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.defaultTimeLimiter = defaultTimeLimiter;
        this.activeTimeLimiter = defaultTimeLimiter;
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * Executes an HTTP call wrapped with circuit breaker, retry, and timeout resilience.
     *
     * @param url          the target URL
     * @param method       the HTTP method
     * @param body         the request body (may be null)
     * @param responseType the expected response type
     * @param headers      additional headers to include (may be null)
     * @param <T>          the response type parameter
     * @return the deserialized response body
     * @throws RestClientException      if the HTTP call fails after retries
     * @throws CallNotPermittedException if the circuit breaker is open
     */
    public <T> T execute(
            String url,
            HttpMethod method,
            Object body,
            Class<T> responseType,
            Map<String, String> headers
    ) {
        Callable<T> httpCall = () -> doExecute(url, method, body, responseType, headers);

        TimeLimiter currentLimiter = this.activeTimeLimiter;
        Callable<T> timedCall = TimeLimiter.decorateFutureSupplier(
                currentLimiter,
                () -> executorService.submit(httpCall::call)
        );

        Callable<T> retryCall = Retry.decorateCallable(retry, timedCall);
        Callable<T> breakerCall = CircuitBreaker.decorateCallable(circuitBreaker, retryCall);

        try {
            return breakerCall.call();
        } catch (CallNotPermittedException ex) {
            log.warn("Circuit breaker open for URL {}: {}", url, ex.getMessage());
            throw ex;
        } catch (RestClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RestClientException("HTTP call failed for URL " + url, ex);
        }
    }

    private <T> T doExecute(
            String url,
            HttpMethod method,
            Object body,
            Class<T> responseType,
            Map<String, String> headers
    ) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::set);
        }

        HttpEntity<Object> entity = new HttpEntity<>(body, httpHeaders);
        log.debug("Executing {} {} (body={})", method, url, body != null ? "present" : "empty");

        ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
        return response.getBody();
    }

    /**
     * Configures the time limiter timeout based on the call context deadline.
     * Creates a new TimeLimiter instance with the specified timeout duration.
     *
     * @param context the provider call context containing the deadline
     */
    public void configureTimeout(ProviderCallContext context) {
        if (context != null && context.deadline() != null) {
            Duration timeout = context.deadline();
            log.debug("Configuring time limiter timeout to {}ms", timeout.toMillis());
            TimeLimiterConfig config = TimeLimiterConfig.custom()
                    .timeoutDuration(timeout)
                    .build();
            this.activeTimeLimiter = TimeLimiter.of("dynamic-tl", config);
        }
    }

    /**
     * Returns the currently active time limiter (for testing).
     *
     * @return the active time limiter
     */
    TimeLimiter getActiveTimeLimiter() {
        return activeTimeLimiter;
    }

    /**
     * Returns the default time limiter (for testing).
     *
     * @return the default time limiter
     */
    TimeLimiter getDefaultTimeLimiter() {
        return defaultTimeLimiter;
    }
}
