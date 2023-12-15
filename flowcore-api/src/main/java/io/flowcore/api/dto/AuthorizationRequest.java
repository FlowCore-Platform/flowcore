package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Authorization evaluation request.
 */
public record AuthorizationRequest(
    @NotNull SubjectAttributes subject,
    @NotNull ObjectAttributes object,
    @NotBlank String action,
    @NotNull EnvironmentAttributes environment
) {}
