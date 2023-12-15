package io.flowcore.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Configuration properties for the FlowCore security module.
 */
@ConfigurationProperties(prefix = "flowcore.security")
public class SecurityProperties {

    private String jwtPublicKey;
    private String jwkSetUri;
    private long jwtClockSkewSeconds = 30;
    private Set<String> jwtIssuers = Set.of();
    private Set<String> jwtAudiences = Set.of();
    private List<String> publicEndpoints = List.of("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");
    private int webhookMaxSkewSeconds = 300;

    public SecurityProperties() {
    }

    public String getJwtPublicKey() {
        return jwtPublicKey;
    }

    public void setJwtPublicKey(String jwtPublicKey) {
        this.jwtPublicKey = jwtPublicKey;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public long getJwtClockSkewSeconds() {
        return jwtClockSkewSeconds;
    }

    public void setJwtClockSkewSeconds(long jwtClockSkewSeconds) {
        this.jwtClockSkewSeconds = jwtClockSkewSeconds;
    }

    public Set<String> getJwtIssuers() {
        return jwtIssuers;
    }

    public void setJwtIssuers(Set<String> jwtIssuers) {
        this.jwtIssuers = jwtIssuers;
    }

    public Set<String> getJwtAudiences() {
        return jwtAudiences;
    }

    public void setJwtAudiences(Set<String> jwtAudiences) {
        this.jwtAudiences = jwtAudiences;
    }

    public List<String> getPublicEndpoints() {
        return publicEndpoints;
    }

    public void setPublicEndpoints(List<String> publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    public int getWebhookMaxSkewSeconds() {
        return webhookMaxSkewSeconds;
    }

    public void setWebhookMaxSkewSeconds(int webhookMaxSkewSeconds) {
        this.webhookMaxSkewSeconds = webhookMaxSkewSeconds;
    }
}
