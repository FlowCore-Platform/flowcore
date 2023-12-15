package io.flowcore.integrations.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.flowcore.integrations.http.ResilientHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Auto-configuration for the FlowCore Integrations module.
 * <p>
 * Registers beans for the resilient HTTP client with sensible defaults:
 * <ul>
 *   <li>Circuit breaker: 50-call sliding window, 50% failure threshold, 30s wait in open state</li>
 *   <li>Retry: max 3 attempts, exponential backoff from 200ms to 2s</li>
 *   <li>Time limiter: 5-second default timeout (overridden per-call from context)</li>
 * </ul>
 */
@AutoConfiguration
public class IntegrationsAutoConfiguration {

    /**
     * Default circuit breaker configuration.
     * <ul>
     *   <li>Sliding window size: 50 calls</li>
     *   <li>Failure rate threshold: 50%</li>
     *   <li>Wait duration in open state: 30 seconds</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(CircuitBreaker.class)
    public CircuitBreaker integrationsCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(50)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(5)
                .minimumNumberOfCalls(10)
                .build();
        return CircuitBreaker.of("integrations", config);
    }

    /**
     * Default retry configuration.
     * <ul>
     *   <li>Max attempts: 3</li>
     *   <li>Exponential backoff: 200ms initial, max 2s</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(Retry.class)
    public Retry integrationsRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        Duration.ofMillis(200), 2.0, Duration.ofSeconds(2)
                ))
                .build();
        return Retry.of("integrations", config);
    }

    /**
     * Default time limiter configuration.
     * <ul>
     *   <li>Timeout duration: 5 seconds (overridden per-call)</li>
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(TimeLimiter.class)
    public TimeLimiter integrationsTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        return TimeLimiter.of("integrations", config);
    }

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean(ResilientHttpClient.class)
    public ResilientHttpClient resilientHttpClient(
            RestTemplate restTemplate,
            CircuitBreaker integrationsCircuitBreaker,
            Retry integrationsRetry,
            TimeLimiter integrationsTimeLimiter
    ) {
        return new ResilientHttpClient(
                restTemplate,
                integrationsCircuitBreaker,
                integrationsRetry,
                integrationsTimeLimiter
        );
    }
}
