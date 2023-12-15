package io.flowcore.security.authz;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link PolicyEvaluator} that evaluates a configurable
 * list of {@link PolicyRule}s against an authorization request.
 * <p>
 * Evaluation order:
 * <ol>
 *     <li>If any rule matches and its decision is {@code DENY}, the result is DENY.</li>
 *     <li>If any rule matches and its decision is {@code ALLOW}, the result is ALLOW.</li>
 *     <li>Default: DENY.</li>
 * </ol>
 */
public class DefaultPolicyEvaluator implements PolicyEvaluator {

    private final List<PolicyRule> rules;

    /**
     * Constructs a new {@code DefaultPolicyEvaluator} with the given policy rules.
     *
     * @param rules the ordered list of policy rules to evaluate
     */
    public DefaultPolicyEvaluator(List<PolicyRule> rules) {
        this.rules = rules != null ? List.copyOf(rules) : List.of();
    }

    @Override
    public AuthorizationDecision evaluate(List<String> subjectRoles,
                                          ObjectAttributes objectAttrs,
                                          String action,
                                          Map<String, String> environment) {
        AuthorizationDecision allowDecision = null;

        for (PolicyRule rule : rules) {
            if (matches(rule, subjectRoles, objectAttrs, action)) {
                if (!rule.decision().allowed()) {
                    return rule.decision();
                }
                // Record the first matching ALLOW for later use
                if (allowDecision == null) {
                    allowDecision = rule.decision();
                }
            }
        }

        if (allowDecision != null) {
            return allowDecision;
        }

        return AuthorizationDecision.deny("No matching policy rule found");
    }

    private boolean matches(PolicyRule rule,
                            List<String> subjectRoles,
                            ObjectAttributes objectAttrs,
                            String action) {
        // Check action match
        if (!rule.action().equals(action)) {
            return false;
        }

        // Check role match: if rule specifies required roles, subject must have at least one
        if (!rule.requiredRoles().isEmpty()) {
            boolean roleMatch = subjectRoles.stream()
                    .anyMatch(rule.requiredRoles()::contains);
            if (!roleMatch) {
                return false;
            }
        }

        // Check ABAC condition if present
        if (rule.condition() != null) {
            return rule.condition().test(objectAttrs);
        }

        return true;
    }
}
