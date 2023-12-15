package io.flowcore.tx.idempotency;

import io.flowcore.api.IdempotencyService;
import io.flowcore.api.dto.StoredResponse;
import io.flowcore.tx.config.TransactionProperties;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link IdempotencyService}.
 * <p>
 * Uses a PostgreSQL-backed JPA store with a unique constraint on
 * {@code (scope, key)} to guarantee at-most-once storage.
 * A scheduled job cleans up expired keys periodically.
 */
@Service
public class DefaultIdempotencyService implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(DefaultIdempotencyService.class);

    private final IdempotencyKeyRepository repository;
    private final TransactionProperties properties;

    public DefaultIdempotencyService(IdempotencyKeyRepository repository,
                                     TransactionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public Optional<StoredResponse> find(String scope, String key, String requestHash) {
        Optional<IdempotencyKeyEntity> found = repository.findByScopeAndKey(scope, key);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyKeyEntity entity = found.get();

        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        if (entity.getRequestHash() != null && !entity.getRequestHash().equals(requestHash)) {
            log.warn("Idempotency key conflict: scope={}, key={}, stored hash differs from request hash", scope, key);
            return Optional.empty();
        }

        return Optional.of(new StoredResponse(
                entity.getScope(),
                entity.getKey(),
                entity.getRequestHash(),
                entity.getResponseJson()
        ));
    }

    @Override
    @Transactional
    public StoredResponse store(String scope, String key, String requestHash, String responseJson,
                                Instant expiresAt) {
        IdempotencyKeyEntity entity = IdempotencyKeyEntity.builder()
                .id(UUID.randomUUID())
                .scope(scope)
                .key(key)
                .requestHash(requestHash)
                .responseJson(responseJson)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();

        try {
            repository.saveAndFlush(entity);
            return new StoredResponse(scope, key, requestHash, responseJson);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Idempotency key already exists: scope={}, key={}", scope, key);
            // Return the existing entry
            Optional<IdempotencyKeyEntity> existing = repository.findByScopeAndKey(scope, key);
            if (existing.isPresent()) {
                IdempotencyKeyEntity e = existing.get();
                return new StoredResponse(e.getScope(), e.getKey(), e.getRequestHash(), e.getResponseJson());
            }
            throw ex;
        }
    }

    @Scheduled(fixedDelayString = "${flowcore.tx.idempotency.cleanup-interval-ms:60000}")
    public void cleanupExpiredKeys() {
        try {
            repository.deleteByExpiresAtBefore(Instant.now());
        } catch (Exception ex) {
            log.error("Failed to clean up expired idempotency keys", ex);
        }
    }
}
