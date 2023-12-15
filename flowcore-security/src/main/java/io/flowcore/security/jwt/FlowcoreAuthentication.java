package io.flowcore.security.jwt;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Spring Security {@link Authentication} implementation that carries
 * the validated Flowcore JWT claims.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowcoreAuthentication implements Authentication {

    private static final long serialVersionUID = 1L;

    private final String subject;
    private final List<GrantedAuthority> authorities;
    private final String tenant;
    private final List<String> scopes;
    private final JwtClaims claims;
    private boolean authenticated = true;

    /**
     * Constructs a new authenticated {@code FlowcoreAuthentication}.
     *
     * @param subject    the subject (user/service ID)
     * @param roles      the assigned roles
     * @param tenant     the tenant identifier
     * @param scopes     the OAuth 2.0 scopes
     * @param claims     the full JWT claims
     */
    public FlowcoreAuthentication(String subject,
                                  List<String> roles,
                                  String tenant,
                                  List<String> scopes,
                                  JwtClaims claims) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.authorities = (roles != null ? roles : List.<String>of())
                .stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        this.tenant = tenant != null ? tenant : "";
        this.scopes = scopes != null ? List.copyOf(scopes) : List.of();
        this.claims = claims;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return claims;
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return subject;
    }

    /**
     * @return the tenant identifier
     */
    public String getTenant() {
        return tenant;
    }

    /**
     * @return the OAuth 2.0 scopes
     */
    public List<String> getScopes() {
        return scopes;
    }

    /**
     * @return the full JWT claims
     */
    public JwtClaims getClaims() {
        return claims;
    }

    /**
     * @return the subject identifier
     */
    public String getSubject() {
        return subject;
    }

    @Override
    public String toString() {
        return "FlowcoreAuthentication{subject='" + subject
                + "', tenant='" + tenant
                + "', authorities=" + authorities + '}';
    }
}
