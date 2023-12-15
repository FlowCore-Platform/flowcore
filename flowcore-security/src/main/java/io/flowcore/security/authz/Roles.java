package io.flowcore.security.authz;

/**
 * Standard role constants used throughout the Flowcore authorization system.
 */
public final class Roles {

    private Roles() {
        // prevent instantiation
    }

    public static final String ADMIN = "ADMIN";
    public static final String OPERATOR = "OPERATOR";
    public static final String VIEWER = "VIEWER";
}
