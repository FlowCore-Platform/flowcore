package io.flowcore.obs.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Central metrics registry for all Flowcore Prometheus metrics.
 * <p>
 * Provides typed increment/record methods for each of the 23 required metrics
 * grouped into counters, gauges, and histograms.
 */
@Component
public class FlowcoreMetrics {

    // ── Metric name constants ────────────────────────────────────────────

    // Counters
    static final String CMD_TOTAL = "flowcore_command_total";
    static final String WF_STARTED = "flowcore_workflow_started_total";
    static final String WF_COMPLETED = "flowcore_workflow_completed_total";
    static final String WF_FAILED = "flowcore_workflow_failed_total";
    static final String TRANSITION = "flowcore_transition_total";
    static final String STEP_RETRY = "flowcore_step_retry_total";
    static final String STEP_TIMEOUT = "flowcore_step_timeout_total";
    static final String COMPENSATION = "flowcore_compensation_total";
    static final String OUTBOX_PUBLISH = "flowcore_outbox_publish_total";
    static final String OUTBOX_DEAD = "flowcore_outbox_dead_total";
    static final String INBOX_DEDUP = "flowcore_inbox_dedup_hits_total";
    static final String IDEMPOTENCY_REPLAY = "flowcore_idempotency_replay_total";
    static final String PROVIDER_CALL = "flowcore_provider_call_total";
    static final String AUTHZ_DECISION = "flowcore_authz_decision_total";
    static final String SECURITY_SIG_FAIL = "flowcore_security_signature_fail_total";
    static final String TIMER_SCHEDULED = "flowcore_timer_scheduled_total";
    static final String TIMER_FIRED = "flowcore_timer_fired_total";

    // Gauges
    static final String WF_INSTANCES = "flowcore_workflow_instances";
    static final String OUTBOX_PENDING = "flowcore_outbox_pending";

    // Histograms / Timers
    static final String CMD_DURATION = "flowcore_command_duration_seconds";
    static final String STEP_DURATION = "flowcore_step_duration_seconds";
    static final String OUTBOX_PUB_DURATION = "flowcore_outbox_publish_duration_seconds";
    static final String PROVIDER_CALL_DURATION = "flowcore_provider_call_duration_seconds";

    private final MeterRegistry registry;

    /**
     * Backing store for gauge values keyed by composite label string.
     * Package-private for test access.
     */
    final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();

    public FlowcoreMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ── Counter methods ──────────────────────────────────────────────────

    /**
     * Records a processed command.
     */
    public void incrementCommandTotal(String command, String source, String result) {
        Counter.builder(CMD_TOTAL)
                .tag("command", command)
                .tag("source", source)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Records a started workflow.
     */
    public void incrementWorkflowStarted(String workflowType) {
        Counter.builder(WF_STARTED)
                .tag("workflow_type", workflowType)
                .register(registry)
                .increment();
    }

    /**
     * Records a successfully completed workflow.
     */
    public void incrementWorkflowCompleted(String workflowType) {
        Counter.builder(WF_COMPLETED)
                .tag("workflow_type", workflowType)
                .register(registry)
                .increment();
    }

    /**
     * Records a failed workflow.
     */
    public void incrementWorkflowFailed(String workflowType, String reason) {
        Counter.builder(WF_FAILED)
                .tag("workflow_type", workflowType)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * Records a state-machine transition.
     */
    public void incrementTransition(String workflowType, String transition, String result) {
        Counter.builder(TRANSITION)
                .tag("workflow_type", workflowType)
                .tag("transition", transition)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Records a step retry attempt.
     */
    public void incrementStepRetry(String workflowType, String step) {
        Counter.builder(STEP_RETRY)
                .tag("workflow_type", workflowType)
                .tag("step", step)
                .register(registry)
                .increment();
    }

    /**
     * Records a step timeout.
     */
    public void incrementStepTimeout(String workflowType, String step) {
        Counter.builder(STEP_TIMEOUT)
                .tag("workflow_type", workflowType)
                .tag("step", step)
                .register(registry)
                .increment();
    }

    /**
     * Records a compensation execution.
     */
    public void incrementCompensation(String workflowType, String step, String result) {
        Counter.builder(COMPENSATION)
                .tag("workflow_type", workflowType)
                .tag("step", step)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Records an outbox publish attempt.
     */
    public void incrementOutboxPublish(String topic, String result) {
        Counter.builder(OUTBOX_PUBLISH)
                .tag("topic", topic)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Records a message sent to the dead-letter topic.
     */
    public void incrementOutboxDead(String topic) {
        Counter.builder(OUTBOX_DEAD)
                .tag("topic", topic)
                .register(registry)
                .increment();
    }

    /**
     * Records an inbox deduplication hit.
     */
    public void incrementInboxDedupHits(String source, String consumerGroup) {
        Counter.builder(INBOX_DEDUP)
                .tag("source", source)
                .tag("consumer_group", consumerGroup)
                .register(registry)
                .increment();
    }

    /**
     * Records an idempotency replay.
     */
    public void incrementIdempotencyReplay(String scope) {
        Counter.builder(IDEMPOTENCY_REPLAY)
                .tag("scope", scope)
                .register(registry)
                .increment();
    }

    /**
     * Records a provider call.
     */
    public void incrementProviderCall(String provider, String operation, String result) {
        Counter.builder(PROVIDER_CALL)
                .tag("provider", provider)
                .tag("operation", operation)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * Records an authorization decision.
     */
    public void incrementAuthzDecision(String decision, String action) {
        Counter.builder(AUTHZ_DECISION)
                .tag("decision", decision)
                .tag("action", action)
                .register(registry)
                .increment();
    }

    /**
     * Records a security signature verification failure.
     */
    public void incrementSecuritySignatureFail(String kind) {
        Counter.builder(SECURITY_SIG_FAIL)
                .tag("kind", kind)
                .register(registry)
                .increment();
    }

    /**
     * Records a scheduled timer.
     */
    public void incrementTimerScheduled(String workflowType, String timer) {
        Counter.builder(TIMER_SCHEDULED)
                .tag("workflow_type", workflowType)
                .tag("timer", timer)
                .register(registry)
                .increment();
    }

    /**
     * Records a fired timer.
     */
    public void incrementTimerFired(String workflowType, String timer) {
        Counter.builder(TIMER_FIRED)
                .tag("workflow_type", workflowType)
                .tag("timer", timer)
                .register(registry)
                .increment();
    }

    // ── Gauge methods ────────────────────────────────────────────────────

    /**
     * Registers (or updates) a gauge tracking the number of active workflow instances.
     * The gauge reads from an internal {@link AtomicLong} supplier.
     *
     * @param workflowType workflow type label
     * @param status       status label (e.g. RUNNING, WAITING)
     * @param supplier     supplier that provides the current count
     */
    public void registerWorkflowInstancesGauge(String workflowType, String status, Supplier<Number> supplier) {
        String key = WF_INSTANCES + "|" + workflowType + "|" + status;
        gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder(WF_INSTANCES, () -> supplier.get().doubleValue())
                    .tag("workflow_type", workflowType)
                    .tag("status", status)
                    .register(registry);
            return value;
        });
    }

    /**
     * Sets the workflow instances gauge value directly.
     */
    public void setWorkflowInstances(String workflowType, String status, long count) {
        String key = WF_INSTANCES + "|" + workflowType + "|" + status;
        AtomicLong gauge = gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder(WF_INSTANCES, value, AtomicLong::get)
                    .tag("workflow_type", workflowType)
                    .tag("status", status)
                    .register(registry);
            return value;
        });
        gauge.set(count);
    }

    /**
     * Registers (or updates) a gauge tracking pending outbox events.
     *
     * @param aggregateType aggregate type label
     * @param supplier      supplier that provides the current count
     */
    public void registerOutboxPendingGauge(String aggregateType, Supplier<Number> supplier) {
        String key = OUTBOX_PENDING + "|" + aggregateType;
        gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder(OUTBOX_PENDING, () -> supplier.get().doubleValue())
                    .tag("aggregate_type", aggregateType)
                    .register(registry);
            return value;
        });
    }

    /**
     * Sets the outbox pending gauge value directly.
     */
    public void setOutboxPending(String aggregateType, long count) {
        String key = OUTBOX_PENDING + "|" + aggregateType;
        AtomicLong gauge = gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder(OUTBOX_PENDING, value, AtomicLong::get)
                    .tag("aggregate_type", aggregateType)
                    .register(registry);
            return value;
        });
        gauge.set(count);
    }

    // ── Histogram / Timer methods ────────────────────────────────────────

    /**
     * Records command processing duration.
     */
    public void recordCommandDuration(String command, String source, Duration duration) {
        Timer.builder(CMD_DURATION)
                .tag("command", command)
                .tag("source", source)
                .register(registry)
                .record(duration);
    }

    /**
     * Records step execution duration.
     */
    public void recordStepDuration(String workflowType, String step, String result, Duration duration) {
        Timer.builder(STEP_DURATION)
                .tag("workflow_type", workflowType)
                .tag("step", step)
                .tag("result", result)
                .register(registry)
                .record(duration);
    }

    /**
     * Records outbox publish duration.
     */
    public void recordOutboxPublishDuration(String topic, Duration duration) {
        Timer.builder(OUTBOX_PUB_DURATION)
                .tag("topic", topic)
                .register(registry)
                .record(duration);
    }

    /**
     * Records external provider call duration.
     */
    public void recordProviderCallDuration(String provider, String operation, Duration duration) {
        Timer.builder(PROVIDER_CALL_DURATION)
                .tag("provider", provider)
                .tag("operation", operation)
                .register(registry)
                .record(duration);
    }
}
