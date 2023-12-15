package io.flowcore.tx.timer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.OutboxService;
import io.flowcore.api.dto.OutboxEventDraft;
import io.flowcore.tx.config.TransactionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimerSchedulerTest {

    @Mock
    private WorkflowTimerRepository timerRepository;

    @Mock
    private OutboxService outboxService;

    private TransactionProperties properties;
    private ObjectMapper objectMapper;
    private TimerScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new TransactionProperties();
        objectMapper = new ObjectMapper();
        scheduler = new TimerScheduler(timerRepository, outboxService, properties, objectMapper);
    }

    // ---- Helper ----

    private WorkflowTimerEntity buildTimer(UUID id, String timerName) {
        return WorkflowTimerEntity.builder()
                .id(id)
                .workflowInstanceId(UUID.randomUUID())
                .tokenId(UUID.randomUUID())
                .timerName(timerName)
                .dueAt(Instant.now().minusSeconds(10))
                .status("SCHEDULED")
                .createdAt(Instant.now().minusSeconds(300))
                .build();
    }

    // ========================================================================
    // pollAndFire()
    // ========================================================================

    @Nested
    @DisplayName("pollAndFire")
    class PollAndFireTests {

        @Test
        @DisplayName("should do nothing when no due timers")
        void shouldDoNothingWhenNoTimers() {
            when(timerRepository.findDueTimers(any(Instant.class), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            scheduler.pollAndFire();

            verify(outboxService, never()).enqueue(any());
        }

        @Test
        @DisplayName("should set status to FIRED and enqueue outbox event for due timer")
        void shouldFireTimerAndEnqueueEvent() {
            UUID timerId = UUID.randomUUID();
            WorkflowTimerEntity timer = buildTimer(timerId, "timeout-5m");

            when(timerRepository.findDueTimers(any(Instant.class), any(Pageable.class)))
                    .thenReturn(List.of(timer));

            scheduler.pollAndFire();

            // Verify status changed to FIRED
            assertThat(timer.getStatus()).isEqualTo("FIRED");

            // Verify outbox event was enqueued
            ArgumentCaptor<OutboxEventDraft> draftCaptor = ArgumentCaptor.forClass(OutboxEventDraft.class);
            verify(outboxService).enqueue(draftCaptor.capture());

            OutboxEventDraft draft = draftCaptor.getValue();
            assertThat(draft.aggregateType()).isEqualTo("workflow_timer");
            assertThat(draft.aggregateId()).isEqualTo(timer.getWorkflowInstanceId().toString());
            assertThat(draft.eventType()).isEqualTo("TimerFiredCommand");
            assertThat(draft.eventKey()).isEqualTo("timer:" + timerId);
            assertThat(draft.payloadJson()).isNotBlank();
            assertThat(draft.headers())
                    .containsEntry("timerId", timerId.toString())
                    .containsEntry("timerName", "timeout-5m")
                    .containsEntry("tokenId", timer.getTokenId().toString());
        }

        @Test
        @DisplayName("should process multiple timers in a single batch")
        void shouldProcessMultipleTimers() {
            WorkflowTimerEntity timer1 = buildTimer(UUID.randomUUID(), "timer-a");
            WorkflowTimerEntity timer2 = buildTimer(UUID.randomUUID(), "timer-b");

            when(timerRepository.findDueTimers(any(Instant.class), any(Pageable.class)))
                    .thenReturn(List.of(timer1, timer2));

            scheduler.pollAndFire();

            assertThat(timer1.getStatus()).isEqualTo("FIRED");
            assertThat(timer2.getStatus()).isEqualTo("FIRED");
            verify(outboxService, times(2)).enqueue(any(OutboxEventDraft.class));
        }

        @Test
        @DisplayName("should include dueAt in serialized payload JSON")
        void shouldIncludeDueAtInPayload() throws Exception {
            UUID timerId = UUID.randomUUID();
            Instant dueAt = Instant.now().minusSeconds(10);
            WorkflowTimerEntity timer = WorkflowTimerEntity.builder()
                    .id(timerId)
                    .workflowInstanceId(UUID.randomUUID())
                    .tokenId(UUID.randomUUID())
                    .timerName("deadline")
                    .dueAt(dueAt)
                    .status("SCHEDULED")
                    .createdAt(Instant.now().minusSeconds(300))
                    .build();

            when(timerRepository.findDueTimers(any(Instant.class), any(Pageable.class)))
                    .thenReturn(List.of(timer));

            scheduler.pollAndFire();

            ArgumentCaptor<OutboxEventDraft> draftCaptor = ArgumentCaptor.forClass(OutboxEventDraft.class);
            verify(outboxService).enqueue(draftCaptor.capture());

            String payloadJson = draftCaptor.getValue().payloadJson();
            assertThat(payloadJson).contains("dueAt");
            assertThat(payloadJson).contains("timerId");
            assertThat(payloadJson).contains("timerName");
            assertThat(payloadJson).contains("workflowInstanceId");
            assertThat(payloadJson).contains("tokenId");
        }

        @Test
        @DisplayName("should use batchSize from properties for pagination")
        void shouldUseBatchSizeFromProperties() {
            when(timerRepository.findDueTimers(any(Instant.class), any(Pageable.class)))
                    .thenReturn(Collections.emptyList());

            scheduler.pollAndFire();

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(timerRepository).findDueTimers(any(Instant.class), pageableCaptor.capture());

            assertThat(pageableCaptor.getValue().getPageSize())
                    .isEqualTo(properties.timer().batchSize());
        }
    }
}
