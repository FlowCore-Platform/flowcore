package io.flowcore.api;

import io.flowcore.api.dto.AuthorizationDecision;
import io.flowcore.api.dto.AuthorizationRequest;

/**
 * Service for evaluating authorization decisions based on ABAC/PBAC policies.
 */
public interface AuthorizationService {

    /**
     * Evaluates whether a request should be allowed or denied.
     *
     * @param request the authorization request containing subject, object, action, and environment
     * @return the decision with an optional reason
     */
    AuthorizationDecision evaluate(AuthorizationRequest request);
}
