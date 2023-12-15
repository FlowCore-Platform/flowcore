package io.flowcore.obs.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every metric defined in {@link FlowcoreMetrics} is properly
 * registered and can be incremented / recorded.
 */
class FlowcoreMetricsTest {

    private SimpleMeterRegistry registry;
    private FlowcoreMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new FlowcoreMetrics(registry);
    }

    // ── Counters ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Counter metrics")
    class CounterTests {

        @Test
        @DisplayName("flowcore_command_total increments correctly")
        void commandTotal() {
            metrics.incrementCommandTotal("StartWorkflow", "api", "success");

            var counter = registry.counter(FlowcoreMetrics.CMD_TOTAL,
                    "command", "StartWorkflow", "source", "api", "result", "success");
            assertThat(counter.count()).isEqualTo(1.0);

            metrics.incrementCommandTotal("StartWorkflow", "api", "success");
            assertThat(counter.count()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("flowcore_workflow_started_total increments correctly")
        void workflowStarted() {
            metrics.incrementWorkflowStarted("card-issuance");

            var counter = registry.counter(FlowcoreMetrics.WF_STARTED,
                    "workflow_type", "card-issuance");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_workflow_completed_total increments correctly")
        void workflowCompleted() {
            metrics.incrementWorkflowCompleted("card-issuance");

            var counter = registry.counter(FlowcoreMetrics.WF_COMPLETED,
                    "workflow_type", "card-issuance");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_workflow_failed_total increments correctly")
        void workflowFailed() {
            metrics.incrementWorkflowFailed("card-issuance", "timeout");

            var counter = registry.counter(FlowcoreMetrics.WF_FAILED,
                    "workflow_type", "card-issuance", "reason", "timeout");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_transition_total increments correctly")
        void transition() {
            metrics.incrementTransition("card-issuance", "validate", "success");

            var counter = registry.counter(FlowcoreMetrics.TRANSITION,
                    "workflow_type", "card-issuance", "transition", "validate", "result", "success");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_step_retry_total increments correctly")
        void stepRetry() {
            metrics.incrementStepRetry("card-issuance", "call-bank");

            var counter = registry.counter(FlowcoreMetrics.STEP_RETRY,
                    "workflow_type", "card-issuance", "step", "call-bank");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_step_timeout_total increments correctly")
        void stepTimeout() {
            metrics.incrementStepTimeout("card-issuance", "call-bank");

            var counter = registry.counter(FlowcoreMetrics.STEP_TIMEOUT,
                    "workflow_type", "card-issuance", "step", "call-bank");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_compensation_total increments correctly")
        void compensation() {
            metrics.incrementCompensation("card-issuance", "call-bank", "success");

            var counter = registry.counter(FlowcoreMetrics.COMPENSATION,
                    "workflow_type", "card-issuance", "step", "call-bank", "result", "success");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_outbox_publish_total increments correctly")
        void outboxPublish() {
            metrics.incrementOutboxPublish("card-events", "success");

            var counter = registry.counter(FlowcoreMetrics.OUTBOX_PUBLISH,
                    "topic", "card-events", "result", "success");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_outbox_dead_total increments correctly")
        void outboxDead() {
            metrics.incrementOutboxDead("card-events");

            var counter = registry.counter(FlowcoreMetrics.OUTBOX_DEAD,
                    "topic", "card-events");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_inbox_dedup_hits_total increments correctly")
        void inboxDedupHits() {
            metrics.incrementInboxDedupHits("kafka", "workflow-engine");

            var counter = registry.counter(FlowcoreMetrics.INBOX_DEDUP,
                    "source", "kafka", "consumer_group", "workflow-engine");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_idempotency_replay_total increments correctly")
        void idempotencyReplay() {
            metrics.incrementIdempotencyReplay("provider-call");

            var counter = registry.counter(FlowcoreMetrics.IDEMPOTENCY_REPLAY,
                    "scope", "provider-call");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_provider_call_total increments correctly")
        void providerCall() {
            metrics.incrementProviderCall("bank-gateway", "createAccount", "success");

            var counter = registry.counter(FlowcoreMetrics.PROVIDER_CALL,
                    "provider", "bank-gateway", "operation", "createAccount", "result", "success");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_authz_decision_total increments correctly")
        void authzDecision() {
            metrics.incrementAuthzDecision("deny", "start-workflow");

            var counter = registry.counter(FlowcoreMetrics.AUTHZ_DECISION,
                    "decision", "deny", "action", "start-workflow");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_security_signature_fail_total increments correctly")
        void securitySignatureFail() {
            metrics.incrementSecuritySignatureFail("hmac");

            var counter = registry.counter(FlowcoreMetrics.SECURITY_SIG_FAIL,
                    "kind", "hmac");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_timer_scheduled_total increments correctly")
        void timerScheduled() {
            metrics.incrementTimerScheduled("card-issuance", "activation-deadline");

            var counter = registry.counter(FlowcoreMetrics.TIMER_SCHEDULED,
                    "workflow_type", "card-issuance", "timer", "activation-deadline");
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("flowcore_timer_fired_total increments correctly")
        void timerFired() {
            metrics.incrementTimerFired("card-issuance", "activation-deadline");

            var counter = registry.counter(FlowcoreMetrics.TIMER_FIRED,
                    "workflow_type", "card-issuance", "timer", "activation-deadline");
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    // ── Gauges ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gauge metrics")
    class GaugeTests {

        @Test
        @DisplayName("flowcore_workflow_instances gauge reflects set value")
        void workflowInstances() {
            metrics.setWorkflowInstances("card-issuance", "RUNNING", 42L);

            var gauge = registry.find(FlowcoreMetrics.WF_INSTANCES).gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(42.0);
        }

        @Test
        @DisplayName("flowcore_workflow_instances gauge updates on subsequent set")
        void workflowInstancesUpdate() {
            metrics.setWorkflowInstances("card-issuance", "RUNNING", 10L);
            metrics.setWorkflowInstances("card-issuance", "RUNNING", 25L);

            var gauge = registry.find(FlowcoreMetrics.WF_INSTANCES).gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(25.0);
        }

        @Test
        @DisplayName("flowcore_outbox_pending gauge reflects set value")
        void outboxPending() {
            metrics.setOutboxPending("CardIssuanceAggregate", 7L);

            var gauge = registry.find(FlowcoreMetrics.OUTBOX_PENDING).gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(7.0);
        }

        @Test
        @DisplayName("flowcore_outbox_pending gauge updates on subsequent set")
        void outboxPendingUpdate() {
            metrics.setOutboxPending("CardIssuanceAggregate", 3L);
            metrics.setOutboxPending("CardIssuanceAggregate", 15L);

            var gauge = registry.find(FlowcoreMetrics.OUTBOX_PENDING).gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(15.0);
        }
    }

    // ── Histograms / Timers ──────────────────────────────────────────────

    @Nested
    @DisplayName("Histogram / Timer metrics")
    class TimerTests {

        @Test
        @DisplayName("flowcore_command_duration_seconds records duration")
        void commandDuration() {
            metrics.recordCommandDuration("StartWorkflow", "api", Duration.ofMillis(150));

            var timer = registry.find(FlowcoreMetrics.CMD_DURATION).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isGreaterThanOrEqualTo(150.0);
        }

        @Test
        @DisplayName("flowcore_step_duration_seconds records duration")
        void stepDuration() {
            metrics.recordStepDuration("card-issuance", "validate", "success", Duration.ofMillis(200));

            var timer = registry.find(FlowcoreMetrics.STEP_DURATION).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isGreaterThanOrEqualTo(200.0);
        }

        @Test
        @DisplayName("flowcore_outbox_publish_duration_seconds records duration")
        void outboxPublishDuration() {
            metrics.recordOutboxPublishDuration("card-events", Duration.ofMillis(50));

            var timer = registry.find(FlowcoreMetrics.OUTBOX_PUB_DURATION).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isGreaterThanOrEqualTo(50.0);
        }

        @Test
        @DisplayName("flowcore_provider_call_duration_seconds records duration")
        void providerCallDuration() {
            metrics.recordProviderCallDuration("bank-gateway", "createAccount", Duration.ofMillis(300));

            var timer = registry.find(FlowcoreMetrics.PROVIDER_CALL_DURATION).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isGreaterThanOrEqualTo(300.0);
        }

        @Test
        @DisplayName("multiple duration recordings accumulate correctly")
        void multipleDurations() {
            metrics.recordCommandDuration("StartWorkflow", "api", Duration.ofMillis(100));
            metrics.recordCommandDuration("StartWorkflow", "api", Duration.ofMillis(200));

            var timer = registry.find(FlowcoreMetrics.CMD_DURATION).timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(2);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isGreaterThanOrEqualTo(300.0);
        }
    }

    // ── Total metric count verification ──────────────────────────────────

    @Test
    @DisplayName("all 23 distinct metrics are registered after exercising every method")
    void allMetricsRegistered() {
        // Counters (17 distinct metric names)
        metrics.incrementCommandTotal("cmd", "api", "ok");
        metrics.incrementWorkflowStarted("wf");
        metrics.incrementWorkflowCompleted("wf");
        metrics.incrementWorkflowFailed("wf", "err");
        metrics.incrementTransition("wf", "t1", "ok");
        metrics.incrementStepRetry("wf", "s1");
        metrics.incrementStepTimeout("wf", "s1");
        metrics.incrementCompensation("wf", "s1", "ok");
        metrics.incrementOutboxPublish("topic", "ok");
        metrics.incrementOutboxDead("topic");
        metrics.incrementInboxDedupHits("src", "cg");
        metrics.incrementIdempotencyReplay("scope");
        metrics.incrementProviderCall("p", "op", "ok");
        metrics.incrementAuthzDecision("allow", "act");
        metrics.incrementSecuritySignatureFail("hmac");
        metrics.incrementTimerScheduled("wf", "t1");
        metrics.incrementTimerFired("wf", "t1");

        // Gauges (2 distinct metric names)
        metrics.setWorkflowInstances("wf", "RUNNING", 1L);
        metrics.setOutboxPending("agg", 1L);

        // Timers (4 distinct metric names)
        metrics.recordCommandDuration("cmd", "api", Duration.ofMillis(1));
        metrics.recordStepDuration("wf", "s1", "ok", Duration.ofMillis(1));
        metrics.recordOutboxPublishDuration("topic", Duration.ofMillis(1));
        metrics.recordProviderCallDuration("p", "op", Duration.ofMillis(1));

        // Verify we have 23 distinct meter IDs
        long distinctNames = registry.getMeters().stream()
                .map(m -> m.getId().getName())
                .distinct()
                .count();
        assertThat(distinctNames).isEqualTo(23);
    }
}
