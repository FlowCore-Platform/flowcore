package io.flowcore.starter;

import io.flowcore.integrations.config.IntegrationsAutoConfiguration;
import io.flowcore.kafka.config.KafkaAutoConfiguration;
import io.flowcore.obs.config.ObservabilityAutoConfiguration;
import io.flowcore.runtime.config.RuntimeAutoConfiguration;
import io.flowcore.security.config.SecurityProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Central auto-configuration entry point for the FlowCore platform.
 * <p>
 * Activated when {@link FlowcoreProperties} is on the classpath.
 * Imports the core module auto-configurations unconditionally and
 * activates optional modules (security, kafka, observability) based
 * on {@code flowcore.*-enabled} properties.
 *
 * <h3>Conditional behaviour</h3>
 * <ul>
 *   <li>{@code flowcore.security-enabled} (default {@code true}) -- enables
 *       {@link SecurityProperties} binding</li>
 *   <li>{@code flowcore.kafka-enabled} (default {@code true}) -- imports
 *       {@link KafkaAutoConfiguration}</li>
 *   <li>{@code flowcore.observability-enabled} (default {@code true}) -- imports
 *       {@link ObservabilityAutoConfiguration}</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(FlowcoreProperties.class)
@EnableConfigurationProperties(FlowcoreProperties.class)
@Import({
        RuntimeAutoConfiguration.class,
        IntegrationsAutoConfiguration.class,
})
public class FlowcoreAutoConfiguration {

    /**
     * Inner static configuration that is activated when observability is enabled.
     */
    @AutoConfiguration
    @ConditionalOnProperty(prefix = "flowcore", name = "observability-enabled",
            havingValue = "true", matchIfMissing = true)
    @Import(ObservabilityAutoConfiguration.class)
    static class ObservabilityConfiguration {
    }

    /**
     * Inner static configuration that is activated when Kafka is enabled.
     */
    @AutoConfiguration
    @ConditionalOnProperty(prefix = "flowcore", name = "kafka-enabled",
            havingValue = "true", matchIfMissing = true)
    @Import(KafkaAutoConfiguration.class)
    static class KafkaConfiguration {
    }

    /**
     * Inner static configuration that is activated when security is enabled.
     * Binds {@link SecurityProperties} so that downstream security beans can
     * be bootstrapped by the security module.
     */
    @AutoConfiguration
    @ConditionalOnProperty(prefix = "flowcore", name = "security-enabled",
            havingValue = "true", matchIfMissing = true)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class SecurityConfiguration {
    }
}
