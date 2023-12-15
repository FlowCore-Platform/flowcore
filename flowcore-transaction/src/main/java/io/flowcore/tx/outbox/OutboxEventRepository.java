package io.flowcore.tx.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEventEntity}.
 * <p>
 * Uses {@code PESSIMISTIC_WRITE} with {@code SKIP LOCKED} semantics
 * to allow multiple instances to safely claim pending outbox events.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Fetches outbox events that are due for publishing, acquiring a
     * pessimistic write lock with skip-locked so that concurrent
     * scheduler instances do not collide.
     *
     * @param now       the current timestamp
     * @param pageable  pagination (limits batch size)
     * @return list of locked outbox events ready to be published
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEventEntity e " +
           "WHERE e.status = 'PENDING' AND e.nextAttemptAt <= :now " +
           "ORDER BY e.nextAttemptAt ASC")
    List<OutboxEventEntity> findDueEvents(@Param("now") Instant now, Pageable pageable);
}
