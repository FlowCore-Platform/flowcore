package io.flowcore.obs.tracing;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Central declaration of all Flowcore observation names and contextual
 * key-name attributes used by Micrometer Tracing.
 * <p>
 * Each {@link ObservationDocumentation} entry maps to an observation that
 * produces a span when a tracing backend (Zipkin, Jaeger, OTel) is present.
 */
public enum FlowcoreObservationConvention implements ObservationDocumentation {

    /**
     * Observation for inbound command processing.
     */
    COMMAND {
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[]{
                    FlowcoreKeyName.COMMAND_NAME,
                    FlowcoreKeyName.COMMAND_SOURCE,
                    FlowcoreKeyName.BUSINESS_KEY
            };
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return FlowcoreCommandConvention.class;
        }
    },

    /**
     * Observation for a state-machine transition.
     */
    WORKFLOW_TRANSITION {
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[]{
                    FlowcoreKeyName.WORKFLOW_TYPE,
                    FlowcoreKeyName.WORKFLOW_INSTANCE_ID,
                    FlowcoreKeyName.BUSINESS_KEY,
                    FlowcoreKeyName.TRANSITION_NAME,
                    FlowcoreKeyName.TRANSITION_RESULT
            };
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return FlowcoreTransitionConvention.class;
        }
    },

    /**
     * Observation for individual step execution.
     */
    STEP_EXECUTE {
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[]{
                    FlowcoreKeyName.WORKFLOW_TYPE,
                    FlowcoreKeyName.WORKFLOW_INSTANCE_ID,
                    FlowcoreKeyName.BUSINESS_KEY,
                    FlowcoreKeyName.STEP_ID,
                    FlowcoreKeyName.STEP_ATTEMPT
            };
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return FlowcoreStepConvention.class;
        }
    },

    /**
     * Observation for enqueuing an event into the outbox table.
     */
    OUTBOX_ENQUEUE {
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[]{
                    FlowcoreKeyName.WORKFLOW_TYPE,
                    FlowcoreKeyName.WORKFLOW_INSTANCE_ID,
                    FlowcoreKeyName.BUSINESS_KEY,
                    FlowcoreKeyName.OUTBOX_TOPIC,
                    FlowcoreKeyName.OUTBOX_AGGREGATE_TYPE
            };
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return FlowcoreOutboxEnqueueConvention.class;
        }
    },

    /**
     * Observation for publishing an outbox event to a message broker.
     */
    OUTBOX_PUBLISH {
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[]{
                    FlowcoreKeyName.OUTBOX_TOPIC,
                    FlowcoreKeyName.OUTBOX_AGGREGATE_TYPE,
                    FlowcoreKeyName.OUTBOX_EVENT_ID
            };
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return FlowcoreOutboxPublishConvention.class;
        }
    },

    /**
     * Observation for inbox deduplication check.
     */
    INBOX_DEDUP_CHECK {
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return new KeyName[]{
                    FlowcoreKeyName.INBOX_SOURCE,
                    FlowcoreKeyName.INBOX_CONSUMER_GROUP,
                    FlowcoreKeyName.INBOX_MESSAGE_ID
            };
        }

        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return FlowcoreInboxDedupConvention.class;
        }
    };

    // ── High-cardinality key names ──────────────────────────────────────

    /**
     * All contextual attribute key names shared across Flowcore observations.
     */
    enum FlowcoreKeyName implements KeyName {
        WORKFLOW_TYPE("workflow.type"),
        WORKFLOW_INSTANCE_ID("workflow.instance_id"),
        BUSINESS_KEY("business_key"),
        COMMAND_NAME("command.name"),
        COMMAND_SOURCE("command.source"),
        STEP_ID("step.id"),
        STEP_ATTEMPT("step.attempt"),
        TRANSITION_NAME("transition.name"),
        TRANSITION_RESULT("transition.result"),
        OUTBOX_TOPIC("outbox.topic"),
        OUTBOX_AGGREGATE_TYPE("outbox.aggregate_type"),
        OUTBOX_EVENT_ID("outbox.event_id"),
        INBOX_SOURCE("inbox.source"),
        INBOX_CONSUMER_GROUP("inbox.consumer_group"),
        INBOX_MESSAGE_ID("inbox.message_id");

        private final String key;

        FlowcoreKeyName(String key) {
            this.key = key;
        }

        @Override
        public String asString() {
            return key;
        }
    }

    // ── Default convention implementations ──────────────────────────────

    /** Default convention for command observations. */
    public static class FlowcoreCommandConvention
            implements ObservationConvention<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public String getName() {
            return "flowcore.command";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "flowcore:command";
        }
    }

    /** Default convention for workflow transition observations. */
    public static class FlowcoreTransitionConvention
            implements ObservationConvention<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public String getName() {
            return "flowcore.workflow.transition";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "flowcore:workflow:transition";
        }
    }

    /** Default convention for step execution observations. */
    public static class FlowcoreStepConvention
            implements ObservationConvention<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public String getName() {
            return "flowcore.step.execute";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "flowcore:step:execute";
        }
    }

    /** Default convention for outbox enqueue observations. */
    public static class FlowcoreOutboxEnqueueConvention
            implements ObservationConvention<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public String getName() {
            return "flowcore.outbox.enqueue";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "flowcore:outbox:enqueue";
        }
    }

    /** Default convention for outbox publish observations. */
    public static class FlowcoreOutboxPublishConvention
            implements ObservationConvention<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public String getName() {
            return "flowcore.outbox.publish";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "flowcore:outbox:publish";
        }
    }

    /** Default convention for inbox dedup check observations. */
    public static class FlowcoreInboxDedupConvention
            implements ObservationConvention<Observation.Context> {
        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public String getName() {
            return "flowcore.inbox.dedup_check";
        }

        @Override
        public String getContextualName(Observation.Context context) {
            return "flowcore:inbox:dedup_check";
        }
    }
}
