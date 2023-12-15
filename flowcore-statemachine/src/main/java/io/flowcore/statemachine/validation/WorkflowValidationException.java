package io.flowcore.statemachine.validation;

/**
 * Exception thrown when a {@link io.flowcore.api.dto.WorkflowDefinition} fails validation.
 *
 * <p>Contains a structured {@link ErrorCode} identifying the specific rule violation
 * and a human-readable {@code details} message describing the issue.</p>
 */
public class WorkflowValidationException extends RuntimeException {

    /**
     * Machine-readable error code identifying the validation rule that failed.
     */
    public enum ErrorCode {
        UNREACHABLE_STATE,
        DEAD_END_STATE,
        INVALID_WORKFLOW_TYPE,
        INITIAL_STATE_NOT_IN_STATES,
        TRANSITION_REFERENCES_UNKNOWN_STATE,
        DUPLICATE_STEP_ID,
        INVALID_RETRY_POLICY,
        INVALID_TIMEOUT_POLICY,
        FORK_JOIN_MISMATCH
    }

    private final ErrorCode errorCode;
    private final String details;

    /**
     * Create a new validation exception.
     *
     * @param errorCode the specific validation rule that was violated
     * @param details   human-readable description of the problem
     */
    public WorkflowValidationException(ErrorCode errorCode, String details) {
        super(formatMessage(errorCode, details));
        this.errorCode = errorCode;
        this.details = details;
    }

    /**
     * Create a new validation exception with an underlying cause.
     *
     * @param errorCode the specific validation rule that was violated
     * @param details   human-readable description of the problem
     * @param cause     the underlying cause
     */
    public WorkflowValidationException(ErrorCode errorCode, String details, Throwable cause) {
        super(formatMessage(errorCode, details), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }

    private static String formatMessage(ErrorCode errorCode, String details) {
        return "[" + errorCode + "] " + details;
    }
}
