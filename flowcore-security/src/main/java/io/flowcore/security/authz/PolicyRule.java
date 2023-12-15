package io.flowcore.security.authz;

import java.util.List;
import java.util.function.Predicate;

/**
 * A single authorization policy rule combining RBAC role checks with an optional
 * ABAC condition predicate evaluated against object attributes.
 *
 * @param name          human-readable rule name (for audit/debug)
 * @param requiredRoles roles that satisfy the RBAC check (any match); empty means no RBAC requirement
 * @param action        the action this rule governs (e.g. "workflow.start")
 * @param condition     optional ABAC condition evaluated against object attributes;
 *                      {@code null} means no additional condition
 * @param decision      the outcome when this rule matches
 */
public record PolicyRule(
        String name,
        List<String> requiredRoles,
        String action,
        Predicate<ObjectAttributes> condition,
        AuthorizationDecision decision
) {

    /**
     * Compact constructor that ensures a defensive copy of the roles list.
     */
    public PolicyRule {
        requiredRoles = requiredRoles != null ? List.copyOf(requiredRoles) : List.of();
    }
}
