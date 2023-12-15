package io.flowcore.obs.tracing;

import io.flowcore.obs.tracing.FlowcoreObservationConvention.FlowcoreCommandConvention;
import io.flowcore.obs.tracing.FlowcoreObservationConvention.FlowcoreInboxDedupConvention;
import io.flowcore.obs.tracing.FlowcoreObservationConvention.FlowcoreKeyName;
import io.flowcore.obs.tracing.FlowcoreObservationConvention.FlowcoreOutboxEnqueueConvention;
import io.flowcore.obs.tracing.FlowcoreObservationConvention.FlowcoreOutboxPublishConvention;
import io.flowcore.obs.tracing.FlowcoreObservationConvention.FlowcoreStepConvention;
import io.flowcore.obs.tracing.FlowcoreObservationConvention.FlowcoreTransitionConvention;
import io.micrometer.observation.Observation.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all observation conventions and key names are properly declared.
 */
class FlowcoreObservationConventionTest {

    // ── Enum values ──────────────────────────────────────────────────────

    @Test
    @DisplayName("all six observation entries are present")
    void allObservationsPresent() {
        assertThat(FlowcoreObservationConvention.values()).hasSize(6);
        assertThat(FlowcoreObservationConvention.valueOf("COMMAND")).isNotNull();
        assertThat(FlowcoreObservationConvention.valueOf("WORKFLOW_TRANSITION")).isNotNull();
        assertThat(FlowcoreObservationConvention.valueOf("STEP_EXECUTE")).isNotNull();
        assertThat(FlowcoreObservationConvention.valueOf("OUTBOX_ENQUEUE")).isNotNull();
        assertThat(FlowcoreObservationConvention.valueOf("OUTBOX_PUBLISH")).isNotNull();
        assertThat(FlowcoreObservationConvention.valueOf("INBOX_DEDUP_CHECK")).isNotNull();
    }

    // ── Key names ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FlowcoreKeyName entries")
    class KeyNameTests {

        @Test
        @DisplayName("all 15 key names have non-blank string values")
        void allKeyNamesNotBlank() {
            for (FlowcoreKeyName keyName : FlowcoreKeyName.values()) {
                assertThat(keyName.asString())
                        .as("KeyName %s should have a non-blank asString()", keyName)
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("expected key names match their string representation")
        void specificKeyNames() {
            assertThat(FlowcoreKeyName.WORKFLOW_TYPE.asString()).isEqualTo("workflow.type");
            assertThat(FlowcoreKeyName.WORKFLOW_INSTANCE_ID.asString()).isEqualTo("workflow.instance_id");
            assertThat(FlowcoreKeyName.BUSINESS_KEY.asString()).isEqualTo("business_key");
            assertThat(FlowcoreKeyName.COMMAND_NAME.asString()).isEqualTo("command.name");
            assertThat(FlowcoreKeyName.COMMAND_SOURCE.asString()).isEqualTo("command.source");
            assertThat(FlowcoreKeyName.STEP_ID.asString()).isEqualTo("step.id");
            assertThat(FlowcoreKeyName.STEP_ATTEMPT.asString()).isEqualTo("step.attempt");
            assertThat(FlowcoreKeyName.TRANSITION_NAME.asString()).isEqualTo("transition.name");
            assertThat(FlowcoreKeyName.TRANSITION_RESULT.asString()).isEqualTo("transition.result");
            assertThat(FlowcoreKeyName.OUTBOX_TOPIC.asString()).isEqualTo("outbox.topic");
            assertThat(FlowcoreKeyName.OUTBOX_AGGREGATE_TYPE.asString()).isEqualTo("outbox.aggregate_type");
            assertThat(FlowcoreKeyName.OUTBOX_EVENT_ID.asString()).isEqualTo("outbox.event_id");
            assertThat(FlowcoreKeyName.INBOX_SOURCE.asString()).isEqualTo("inbox.source");
            assertThat(FlowcoreKeyName.INBOX_CONSUMER_GROUP.asString()).isEqualTo("inbox.consumer_group");
            assertThat(FlowcoreKeyName.INBOX_MESSAGE_ID.asString()).isEqualTo("inbox.message_id");
        }
    }

    // ── Observation high-cardinality keys ─────────────────────────────────

    @Nested
    @DisplayName("High-cardinality key names per observation")
    class HighCardinalityTests {

        @Test
        @DisplayName("COMMAND exposes command_name, command_source, business_key")
        void commandKeys() {
            var names = FlowcoreObservationConvention.COMMAND.getHighCardinalityKeyNames();
            assertThat(names).hasSize(3);
        }

        @Test
        @DisplayName("WORKFLOW_TRANSITION exposes 5 keys")
        void transitionKeys() {
            var names = FlowcoreObservationConvention.WORKFLOW_TRANSITION.getHighCardinalityKeyNames();
            assertThat(names).hasSize(5);
        }

        @Test
        @DisplayName("STEP_EXECUTE exposes 5 keys")
        void stepKeys() {
            var names = FlowcoreObservationConvention.STEP_EXECUTE.getHighCardinalityKeyNames();
            assertThat(names).hasSize(5);
        }

        @Test
        @DisplayName("OUTBOX_ENQUEUE exposes 5 keys")
        void outboxEnqueueKeys() {
            var names = FlowcoreObservationConvention.OUTBOX_ENQUEUE.getHighCardinalityKeyNames();
            assertThat(names).hasSize(5);
        }

        @Test
        @DisplayName("OUTBOX_PUBLISH exposes 3 keys")
        void outboxPublishKeys() {
            var names = FlowcoreObservationConvention.OUTBOX_PUBLISH.getHighCardinalityKeyNames();
            assertThat(names).hasSize(3);
        }

        @Test
        @DisplayName("INBOX_DEDUP_CHECK exposes 3 keys")
        void inboxDedupKeys() {
            var names = FlowcoreObservationConvention.INBOX_DEDUP_CHECK.getHighCardinalityKeyNames();
            assertThat(names).hasSize(3);
        }
    }

    // ── Convention classes ────────────────────────────────────────────────

    @Nested
    @DisplayName("Convention class implementations")
    class ConventionClassTests {

        @Test
        @DisplayName("FlowcoreCommandConvention returns correct names")
        void commandConvention() {
            var convention = new FlowcoreCommandConvention();
            assertThat(convention.getName()).isEqualTo("flowcore.command");
            assertThat(convention.getContextualName(new Context())).isEqualTo("flowcore:command");
            assertThat(convention.supportsContext(new Context())).isTrue();
        }

        @Test
        @DisplayName("FlowcoreTransitionConvention returns correct names")
        void transitionConvention() {
            var convention = new FlowcoreTransitionConvention();
            assertThat(convention.getName()).isEqualTo("flowcore.workflow.transition");
            assertThat(convention.getContextualName(new Context())).isEqualTo("flowcore:workflow:transition");
            assertThat(convention.supportsContext(new Context())).isTrue();
        }

        @Test
        @DisplayName("FlowcoreStepConvention returns correct names")
        void stepConvention() {
            var convention = new FlowcoreStepConvention();
            assertThat(convention.getName()).isEqualTo("flowcore.step.execute");
            assertThat(convention.getContextualName(new Context())).isEqualTo("flowcore:step:execute");
            assertThat(convention.supportsContext(new Context())).isTrue();
        }

        @Test
        @DisplayName("FlowcoreOutboxEnqueueConvention returns correct names")
        void outboxEnqueueConvention() {
            var convention = new FlowcoreOutboxEnqueueConvention();
            assertThat(convention.getName()).isEqualTo("flowcore.outbox.enqueue");
            assertThat(convention.getContextualName(new Context())).isEqualTo("flowcore:outbox:enqueue");
            assertThat(convention.supportsContext(new Context())).isTrue();
        }

        @Test
        @DisplayName("FlowcoreOutboxPublishConvention returns correct names")
        void outboxPublishConvention() {
            var convention = new FlowcoreOutboxPublishConvention();
            assertThat(convention.getName()).isEqualTo("flowcore.outbox.publish");
            assertThat(convention.getContextualName(new Context())).isEqualTo("flowcore:outbox:publish");
            assertThat(convention.supportsContext(new Context())).isTrue();
        }

        @Test
        @DisplayName("FlowcoreInboxDedupConvention returns correct names")
        void inboxDedupConvention() {
            var convention = new FlowcoreInboxDedupConvention();
            assertThat(convention.getName()).isEqualTo("flowcore.inbox.dedup_check");
            assertThat(convention.getContextualName(new Context())).isEqualTo("flowcore:inbox:dedup_check");
            assertThat(convention.supportsContext(new Context())).isTrue();
        }
    }

    // ── getDefaultConvention ──────────────────────────────────────────────

    @Nested
    @DisplayName("getDefaultConvention returns convention class")
    class DefaultConventionTests {

        @Test
        @DisplayName("COMMAND default convention is FlowcoreCommandConvention")
        void commandDefault() {
            assertThat(FlowcoreObservationConvention.COMMAND.getDefaultConvention())
                    .isEqualTo(FlowcoreCommandConvention.class);
        }

        @Test
        @DisplayName("WORKFLOW_TRANSITION default convention is FlowcoreTransitionConvention")
        void transitionDefault() {
            assertThat(FlowcoreObservationConvention.WORKFLOW_TRANSITION.getDefaultConvention())
                    .isEqualTo(FlowcoreTransitionConvention.class);
        }

        @Test
        @DisplayName("STEP_EXECUTE default convention is FlowcoreStepConvention")
        void stepDefault() {
            assertThat(FlowcoreObservationConvention.STEP_EXECUTE.getDefaultConvention())
                    .isEqualTo(FlowcoreStepConvention.class);
        }

        @Test
        @DisplayName("OUTBOX_ENQUEUE default convention is FlowcoreOutboxEnqueueConvention")
        void outboxEnqueueDefault() {
            assertThat(FlowcoreObservationConvention.OUTBOX_ENQUEUE.getDefaultConvention())
                    .isEqualTo(FlowcoreOutboxEnqueueConvention.class);
        }

        @Test
        @DisplayName("OUTBOX_PUBLISH default convention is FlowcoreOutboxPublishConvention")
        void outboxPublishDefault() {
            assertThat(FlowcoreObservationConvention.OUTBOX_PUBLISH.getDefaultConvention())
                    .isEqualTo(FlowcoreOutboxPublishConvention.class);
        }

        @Test
        @DisplayName("INBOX_DEDUP_CHECK default convention is FlowcoreInboxDedupConvention")
        void inboxDedupDefault() {
            assertThat(FlowcoreObservationConvention.INBOX_DEDUP_CHECK.getDefaultConvention())
                    .isEqualTo(FlowcoreInboxDedupConvention.class);
        }
    }
}
