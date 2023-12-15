package io.flowcore.obs.config;

import io.flowcore.obs.logging.FlowcoreMdcInjector;
import io.flowcore.obs.metrics.FlowcoreMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Flowcore observability infrastructure.
 * <p>
 * Registers the central {@link FlowcoreMetrics} component and ensures
 * {@link FlowcoreMdcInjector} is part of the application context.
 *
 * <p>Activated automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlowcoreMetrics flowcoreMetrics(MeterRegistry meterRegistry) {
        return new FlowcoreMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowcoreMdcInjector flowcoreMdcInjector() {
        return new FlowcoreMdcInjector();
    }
}
