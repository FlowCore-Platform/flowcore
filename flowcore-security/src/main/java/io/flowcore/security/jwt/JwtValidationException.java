package io.flowcore.security.jwt;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown when a JWT token fails validation (expired, malformed, bad signature, etc.).
 */
public class JwtValidationException extends AuthenticationException {

    /**
     * Constructs a new {@code JwtValidationException} with the specified message.
     *
     * @param message the detail message
     */
    public JwtValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code JwtValidationException} with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public JwtValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
