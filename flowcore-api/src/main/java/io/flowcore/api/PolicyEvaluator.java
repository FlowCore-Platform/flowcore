package io.flowcore.api;

import io.flowcore.api.dto.AuthorizationDecision;
import io.flowcore.api.dto.EnvironmentAttributes;
import io.flowcore.api.dto.ObjectAttributes;
import io.flowcore.api.dto.SubjectAttributes;

/**
 * Evaluates authorization policies using attribute-based access control (ABAC).
 */
public interface PolicyEvaluator {

    /**
     * Evaluates an authorization decision based on subject, object, action, and environment attributes.
     *
     * @param subject     attributes of the requesting subject
     * @param object      attributes of the target object
     * @param action      the action being requested
     * @param environment environmental context attributes
     * @return the authorization decision
     */
    AuthorizationDecision evaluate(SubjectAttributes subject, ObjectAttributes object, String action, EnvironmentAttributes environment);
}
