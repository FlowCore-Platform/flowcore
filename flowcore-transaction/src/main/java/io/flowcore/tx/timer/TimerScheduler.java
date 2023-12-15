package io.flowcore.tx.timer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flowcore.api.OutboxService;
import io.flowcore.api.dto.OutboxEventDraft;
import io.flowcore.tx.config.TransactionProperties;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled component that polls for due workflow timers, marks them as
 * FIRED, and publishes a {@code TimerFiredCommand} event through the
 * transactional outbox.
 */
@Service
public class TimerScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimerScheduler.class);

    private static final String TIMER_FIRED_EVENT_TYPE = "TimerFiredCommand";

    private final WorkflowTimerRepository timerRepository;
    private final OutboxService outboxService;
    private final TransactionProperties properties;
    private final ObjectMapper objectMapper;

    public TimerScheduler(WorkflowTimerRepository timerRepository,
                          OutboxService outboxService,
                          TransactionProperties properties,
                          ObjectMapper objectMapper) {
        this.timerRepository = timerRepository;
        this.outboxService = outboxService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${flowcore.tx.timer.poll-interval-ms:500}")
    @Transactional
    public void pollAndFire() {
        int batchSize = properties.timer().batchSize();
        var dueTimers = timerRepository.findDueTimers(Instant.now(), PageRequest.of(0, batchSize));

        if (dueTimers.isEmpty()) {
            return;
        }

        for (var timer : dueTimers) {
            timer.setStatus("FIRED");
            publishTimerFiredEvent(timer);
            log.debug("Fired workflow timer id={}, name={}, workflowInstanceId={}",
                    timer.getId(), timer.getTimerName(), timer.getWorkflowInstanceId());
        }
    }

    private void publishTimerFiredEvent(WorkflowTimerEntity timer) {
        String payloadJson = buildTimerFiredPayload(timer);
        String eventKey = "timer:" + timer.getId();

        OutboxEventDraft draft = new OutboxEventDraft(
                "workflow_timer",
                timer.getWorkflowInstanceId().toString(),
                TIMER_FIRED_EVENT_TYPE,
                eventKey,
                payloadJson,
                Map.of(
                        "timerId", timer.getId().toString(),
                        "timerName", timer.getTimerName(),
                        "tokenId", timer.getTokenId().toString()
                )
        );

        outboxService.enqueue(draft);
    }

    private String buildTimerFiredPayload(WorkflowTimerEntity timer) {
        try {
            var payload = Map.of(
                    "timerId", timer.getId().toString(),
                    "timerName", timer.getTimerName(),
                    "workflowInstanceId", timer.getWorkflowInstanceId().toString(),
                    "tokenId", timer.getTokenId().toString(),
                    "dueAt", timer.getDueAt().toString()
            );
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.error("Failed to serialize timer fired payload for timer id={}", timer.getId(), ex);
            return "{}";
        }
    }
}
