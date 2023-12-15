package io.flowcore.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a workflow token — a concurrency handle used in fork/join scenarios.
 *
 * @param id                   unique token identifier
 * @param workflowInstanceId   the parent workflow instance
 * @param tokenName            logical name of the token (e.g. "fork-1-branch-A")
 * @param activeNode           the current state/step the token is positioned at
 * @param status               token status (ACTIVE, COMPLETED, FAILED)
 * @param createdAt            timestamp when the token was created
 * @param updatedAt            timestamp when the token was last modified
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowToken(
        @NotNull UUID id,
        @NotNull UUID workflowInstanceId,
        @NotBlank String tokenName,
        String activeNode,
        @NotBlank String status,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt
) {}
