package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command representing a timer-fired event for a workflow instance.
 *
 * @param instanceId  the workflow instance identifier
 * @param tokenId     the token associated with the timer
 * @param timerName   the logical name of the timer that fired
 * @param payloadJson optional payload associated with the timer event
 */
public record TimerFiredCommand(
        @NotNull UUID instanceId,
        @NotNull UUID tokenId,
        @NotBlank String timerName,
        String payloadJson
) {}
