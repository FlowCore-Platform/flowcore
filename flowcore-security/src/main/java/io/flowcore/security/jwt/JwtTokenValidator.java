package io.flowcore.security.jwt;

import io.flowcore.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.lang.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Validates JWT tokens using JJWT with RS256 signature verification.
 * Supports both a configured public key and JWK Set URI resolution.
 */
public class JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);

    private final SecurityProperties properties;
    private volatile PublicKey signingKey;

    /**
     * Constructs a new {@code JwtTokenValidator}.
     *
     * @param properties the security configuration properties
     */
    public JwtTokenValidator(SecurityProperties properties) {
        this.properties = properties;
        this.signingKey = loadPublicKey(properties.getJwtPublicKey());
    }

    /**
     * Validates the given JWT token and returns the parsed claims.
     *
     * @param token the raw JWT token string
     * @return the validated JWT claims
     * @throws JwtValidationException if validation fails for any reason
     */
    public JwtClaims validate(String token) {
        if (!Strings.hasText(token)) {
            throw new JwtValidationException("Token is null or empty");
        }

        try {
            var parserBuilder = Jwts.parser();

            if (Strings.hasText(properties.getJwkSetUri())) {
                // JWK Set URI support will be handled via spring-security-oauth2-resource-server
                // or custom implementation. For now, require public key configuration.
                throw new JwtValidationException(
                        "JWK Set URI resolution requires spring-security-oauth2-resource-server");
            } else if (signingKey != null) {
                parserBuilder.verifyWith(signingKey);
            } else {
                throw new JwtValidationException(
                        "No public key or JWK Set URI configured for JWT validation");
            }

            var clockSkew = properties.getJwtClockSkewSeconds();
            parserBuilder.clockSkewSeconds(clockSkew);

            Jws<Claims> jws = parserBuilder.build().parseSignedClaims(token);
            Claims body = jws.getPayload();

            // Verify required claims: iss, sub, aud, exp, iat
            String subject = body.getSubject();
            if (!Strings.hasText(subject)) {
                throw new JwtValidationException("Missing required claim: sub");
            }

            String issuer = body.getIssuer();
            if (!Strings.hasText(issuer)) {
                throw new JwtValidationException("Missing required claim: iss");
            }

            Object audClaim = body.get("aud");
            String audience = resolveAudience(audClaim);
            if (!Strings.hasText(audience)) {
                throw new JwtValidationException("Missing required claim: aud");
            }

            Date issuedAt = body.getIssuedAt();
            if (issuedAt == null) {
                throw new JwtValidationException("Missing required claim: iat");
            }

            Date expiresAt = body.getExpiration();
            if (expiresAt == null) {
                throw new JwtValidationException("Missing required claim: exp");
            }

            // Verify issuer against configured set
            Set<String> validIssuers = properties.getJwtIssuers();
            if (!validIssuers.isEmpty() && !validIssuers.contains(issuer)) {
                throw new JwtValidationException(
                        "Untrusted issuer: " + issuer);
            }

            // Verify audience against configured set
            Set<String> validAudiences = properties.getJwtAudiences();
            if (!validAudiences.isEmpty() && !validAudiences.contains(audience)) {
                throw new JwtValidationException(
                        "Untrusted audience: " + audience);
            }

            // Extract roles (from "roles" claim, fallback to realm_access.roles)
            List<String> roles = extractRoles(body);

            // Extract tenant
            String tenant = body.get("tenant", String.class);
            if (tenant == null) {
                tenant = "";
            }

            // Extract scopes
            List<String> scopes = extractScopes(body);

            return new JwtClaims(
                    subject,
                    roles,
                    tenant,
                    scopes,
                    issuer,
                    audience,
                    issuedAt,
                    expiresAt
            );
        } catch (JwtValidationException ex) {
            throw ex;
        } catch (JwtException ex) {
            log.debug("JWT parsing/signature error: {}", ex.getMessage());
            throw new JwtValidationException("Invalid JWT token: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.debug("Unexpected JWT validation error: {}", ex.getMessage());
            throw new JwtValidationException("JWT validation failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims body) {
        // Try top-level "roles" claim
        Object rolesObj = body.get("roles");
        if (rolesObj instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }

        // Try Keycloak-style realm_access.roles
        Object realmAccess = body.get("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> map) {
            Object realmRoles = map.get("roles");
            if (realmRoles instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
        }

        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractScopes(Claims body) {
        String scope = body.get("scope", String.class);
        if (Strings.hasText(scope)) {
            return List.of(scope.split("\\s+"));
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private String resolveAudience(Object audClaim) {
        if (audClaim == null) {
            return null;
        }
        if (audClaim instanceof String s) {
            return s;
        }
        if (audClaim instanceof List<?> list && !list.isEmpty()) {
            return list.getFirst().toString();
        }
        return null;
    }

    private static PublicKey loadPublicKey(String pemKey) {
        if (!Strings.hasText(pemKey)) {
            return null;
        }
        try {
            String cleaned = pemKey
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] encoded = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse JWT public key", ex);
        }
    }
}
