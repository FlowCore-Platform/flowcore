package io.flowcore.security.authz;

import java.util.List;
import java.util.Map;

/**
 * Request object for an authorization evaluation.
 *
 * @param subjectRoles    roles assigned to the subject
 * @param subjectTenant   tenant of the subject
 * @param subjectSub      unique subject identifier
 * @param action          the action being performed (e.g. "workflow.start")
 * @param objectAttributes attributes of the target object
 * @param environment     environment attributes (time, IP, etc.)
 */
public record AuthorizationRequest(
        List<String> subjectRoles,
        String subjectTenant,
        String subjectSub,
        String action,
        ObjectAttributes objectAttributes,
        Map<String, String> environment
) {

    /**
     * Compact constructor ensuring defensive copies.
     */
    public AuthorizationRequest {
        subjectRoles = subjectRoles != null ? List.copyOf(subjectRoles) : List.of();
        environment = environment != null ? Map.copyOf(environment) : Map.of();
    }
}
