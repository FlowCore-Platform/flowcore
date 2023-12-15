package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Attributes of the requesting subject for ABAC evaluation.
 */
public record SubjectAttributes(
    @NotBlank String sub,
    List<String> roles,
    String tenant,
    List<String> scopes
) {
    public SubjectAttributes {
        roles = roles != null ? List.copyOf(roles) : List.of();
        scopes = scopes != null ? List.copyOf(scopes) : List.of();
    }
}
