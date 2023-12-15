package io.flowcore.security.jwt;

import java.util.Date;
import java.util.List;

/**
 * Represents the validated claims extracted from a JWT token.
 *
 * @param subject   the subject claim (unique user/service identifier)
 * @param roles     the roles assigned to the subject
 * @param tenant    the tenant the subject belongs to
 * @param scopes    the OAuth 2.0 scopes granted
 * @param issuer    the issuer of the token
 * @param audience  the intended audience
 * @param issuedAt  the time at which the token was issued
 * @param expiresAt the expiration time of the token
 */
public record JwtClaims(
        String subject,
        List<String> roles,
        String tenant,
        List<String> scopes,
        String issuer,
        String audience,
        Date issuedAt,
        Date expiresAt
) {
    /**
     * Compact constructor ensuring defensive copies of mutable lists.
     */
    public JwtClaims {
        roles = roles != null ? List.copyOf(roles) : List.of();
        scopes = scopes != null ? List.copyOf(scopes) : List.of();
    }
}
