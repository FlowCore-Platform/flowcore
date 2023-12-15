package io.flowcore.statemachine.guard;

/**
 * Runtime exception thrown when a guard expression cannot be tokenized,
 * parsed, or evaluated.
 *
 * <p>The exception message includes position information (character offset)
 * when the error originates from the lexer or parser, enabling precise
 * error reporting for workflow authors.</p>
 */
public class GuardEvaluationException extends RuntimeException {

    /**
     * Creates a new exception with a message.
     *
     * @param message the detail message
     */
    public GuardEvaluationException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public GuardEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a lexer-level error with position information.
     *
     * @param position character offset in the input where the error occurred
     * @param message  description of the error
     * @return a new exception with formatted position information
     */
    public static GuardEvaluationException atPosition(int position, String message) {
        return new GuardEvaluationException("at position " + position + ": " + message);
    }

    /**
     * Creates a parse-level error with position information.
     *
     * @param position character offset in the input where the error occurred
     * @param expected what was expected
     * @param actual   what was actually found
     * @return a new exception with formatted error details
     */
    public static GuardEvaluationException unexpectedToken(int position, String expected, String actual) {
        return new GuardEvaluationException(
                "at position " + position + ": expected " + expected + " but found " + actual);
    }
}
