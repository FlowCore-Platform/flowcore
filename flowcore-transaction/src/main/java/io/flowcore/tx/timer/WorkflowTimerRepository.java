package io.flowcore.tx.timer;

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
 * Spring Data JPA repository for {@link WorkflowTimerEntity}.
 * <p>
 * Uses {@code PESSIMISTIC_WRITE} with {@code SKIP LOCKED} semantics
 * so that multiple scheduler instances can claim timers without collision.
 */
public interface WorkflowTimerRepository extends JpaRepository<WorkflowTimerEntity, UUID> {

    /**
     * Fetches scheduled timers that are due, acquiring a pessimistic write
     * lock with skip-locked for safe concurrent processing.
     *
     * @param now      the current timestamp
     * @param pageable pagination (limits batch size)
     * @return list of locked timer entities ready to be fired
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT t FROM WorkflowTimerEntity t " +
           "WHERE t.status = 'SCHEDULED' AND t.dueAt <= :now " +
           "ORDER BY t.dueAt ASC")
    List<WorkflowTimerEntity> findDueTimers(@Param("now") Instant now, Pageable pageable);
}
