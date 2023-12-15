package io.flowcore.security.authz;

/**
 * Evaluates policy rules against subject, object, action, and environment attributes.
 */
public interface PolicyEvaluator {

    /**
     * Evaluates the policy for the given authorization parameters.
     *
     * @param subjectRoles  roles assigned to the subject
     * @param objectAttrs   attributes of the target object
     * @param action        the action being performed
     * @param environment   environment attributes
     * @return the authorization decision
     */
    AuthorizationDecision evaluate(java.util.List<String> subjectRoles,
                                   ObjectAttributes objectAttrs,
                                   String action,
                                   java.util.Map<String, String> environment);
}
