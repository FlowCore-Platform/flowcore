package io.flowcore.tx.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.OutboxService;
import io.flowcore.api.dto.FailureInfo;
import io.flowcore.api.dto.OutboxEvent;
import io.flowcore.api.dto.OutboxEventDraft;
import io.flowcore.tx.config.TransactionProperties;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of {@link OutboxService}.
 * <p>
 * Persists outbox events in the database and provides
 * pessimistic-locked batch fetching for the scheduler.
 */
@Service
public class DefaultOutboxService implements OutboxService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOutboxService.class);

    private final OutboxEventRepository repository;
    private final TransactionProperties properties;
    private final ObjectMapper objectMapper;

    public DefaultOutboxService(OutboxEventRepository repository,
                                TransactionProperties properties,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public UUID enqueue(OutboxEventDraft draft) {
        Instant now = Instant.now();
        OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(draft.aggregateType())
                .aggregateId(draft.aggregateId())
                .eventType(draft.eventType())
                .eventKey(draft.eventKey())
                .payloadJson(draft.payloadJson())
                .headersJson(serializeHeaders(draft.headers()))
                .status("PENDING")
                .publishAttempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();

        repository.saveAndFlush(entity);
        log.debug("Enqueued outbox event id={}, type={}, key={}", entity.getId(), entity.getEventType(), entity.getEventKey());
        return entity.getId();
    }

    @Override
    @Transactional
    public List<OutboxEvent> fetchDueBatch(int batchSize) {
        List<OutboxEventEntity> entities = repository.findDueEvents(Instant.now(), PageRequest.of(0, batchSize));
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id, Instant publishedAt) {
        repository.findById(id).ifPresentOrElse(
                entity -> {
                    entity.setStatus("PUBLISHED");
                    entity.setPublishedAt(publishedAt);
                    log.debug("Marked outbox event id={} as PUBLISHED", id);
                },
                () -> log.warn("Outbox event id={} not found when marking as published", id)
        );
    }

    @Override
    @Transactional
    public void markFailed(UUID id, FailureInfo info, Instant nextAttemptAt) {
        repository.findById(id).ifPresentOrElse(
                entity -> {
                    int attempts = entity.getPublishAttempts() + 1;
                    entity.setPublishAttempts(attempts);
                    int maxAttempts = properties.outbox().maxAttempts();
                    if (attempts >= maxAttempts) {
                        entity.setStatus("DEAD");
                        log.error("Outbox event id={} moved to DEAD after {} attempts. errorCode={}, errorDetail={}",
                                id, attempts, info.errorCode(), info.errorDetail());
                    } else {
                        entity.setStatus("PENDING");
                        entity.setNextAttemptAt(nextAttemptAt);
                        log.warn("Outbox event id={} failed (attempt {}/{}). errorCode={}. Next retry at {}",
                                id, attempts, maxAttempts, info.errorCode(), nextAttemptAt);
                    }
                },
                () -> log.warn("Outbox event id={} not found when marking as failed", id)
        );
    }

    private OutboxEvent toDto(OutboxEventEntity entity) {
        return new OutboxEvent(
                entity.getId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getEventKey(),
                entity.getPayloadJson(),
                deserializeHeaders(entity.getHeadersJson()),
                entity.getStatus(),
                entity.getPublishAttempts(),
                entity.getNextAttemptAt(),
                entity.getCreatedAt(),
                entity.getPublishedAt()
        );
    }

    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize outbox headers, storing null", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> deserializeHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize outbox headers, returning empty map", ex);
            return Map.of();
        }
    }
}
