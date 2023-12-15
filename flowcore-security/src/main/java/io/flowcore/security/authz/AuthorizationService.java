package io.flowcore.security.authz;

/**
 * Service for evaluating authorization requests and recording audit logs.
 */
public interface AuthorizationService {

    /**
     * Evaluates the given authorization request.
     *
     * @param request the authorization request
     * @return the authorization decision
     */
    AuthorizationDecision evaluate(AuthorizationRequest request);
}
