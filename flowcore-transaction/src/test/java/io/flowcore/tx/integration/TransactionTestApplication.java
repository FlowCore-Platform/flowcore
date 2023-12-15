package io.flowcore.tx.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.flowcore.tx.config.TransactionProperties;
import io.flowcore.tx.outbox.OutboxPublisher;
import io.flowcore.tx.outbox.PublishResult;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
@EnableJpaRepositories(basePackages = "io.flowcore.tx")
@EntityScan(basePackages = "io.flowcore.tx")
@ComponentScan(
    basePackages = {"io.flowcore.tx"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            io.flowcore.tx.outbox.OutboxPublisherScheduler.class,
            io.flowcore.tx.timer.TimerScheduler.class
        }
    )
)
@EnableConfigurationProperties(TransactionProperties.class)
class TransactionTestApplication {

    public static void main(String[] args) { SpringApplication.run(TransactionTestApplication.class, args); }

    @Bean ObjectMapper objectMapper() { return new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS); }

    @Bean OutboxPublisher outboxPublisher() { return event -> new PublishResult.Success(); }
}
