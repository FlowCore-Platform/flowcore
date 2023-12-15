package io.flowcore.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a running or completed workflow instance.
 *
 * @param id            unique instance identifier
 * @param workflowType  the workflow definition type this instance belongs to
 * @param businessKey   optional application-level correlation key
 * @param status        current lifecycle status (PENDING, RUNNING, SUSPENDED, COMPLETED, FAILED, CANCELLED)
 * @param currentState  the state machine's current state name
 * @param version       optimistic locking version
 * @param contextJson   serialized context data (JSON)
 * @param createdAt     timestamp when the instance was created
 * @param updatedAt     timestamp when the instance was last modified
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowInstance(
        @NotNull UUID id,
        @NotBlank String workflowType,
        String businessKey,
        @NotBlank String status,
        @NotBlank String currentState,
        int version,
        String contextJson,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt
) {}
