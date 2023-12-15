package io.flowcore.tx.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IdempotencyKeyEntity}.
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    /**
     * Looks up an idempotency key by its unique scope+key combination.
     *
     * @param scope the idempotency scope
     * @param key   the idempotency key
     * @return the matching entity, if any
     */
    Optional<IdempotencyKeyEntity> findByScopeAndKey(String scope, String key);

    /**
     * Deletes all expired idempotency keys.
     *
     * @param now the current timestamp; keys with {@code expiresAt} before this are removed
     */
    void deleteByExpiresAtBefore(Instant now);
}
