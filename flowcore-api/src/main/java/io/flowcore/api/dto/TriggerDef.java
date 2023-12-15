package io.flowcore.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Definition of a trigger that causes a state transition.
 *
 * @param type the trigger type (e.g. "event", "timer", "signal")
 * @param name the logical trigger name
 */
public record TriggerDef(
        @NotBlank String type,
        @NotBlank String name
) {}
