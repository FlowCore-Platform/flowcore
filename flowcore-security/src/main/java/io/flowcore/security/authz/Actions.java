package io.flowcore.security.authz;

/**
 * Standard action constants used throughout the Flowcore authorization system.
 */
public final class Actions {

    private Actions() {
        // prevent instantiation
    }

    public static final String WORKFLOW_START = "workflow.start";
    public static final String WORKFLOW_SIGNAL = "workflow.signal";
    public static final String WORKFLOW_VIEW = "workflow.view";

    public static final String CARD_ISSUE = "card.issue";
    public static final String CARD_VIEW = "card.view";

    public static final String PAYMENT_INITIATE = "payment.initiate";
    public static final String PAYMENT_CAPTURE = "payment.capture";
    public static final String PAYMENT_REFUND = "payment.refund";
    public static final String PAYMENT_VIEW = "payment.view";

    public static final String ADMIN_METRICS_VIEW = "admin.metrics.view";
}
