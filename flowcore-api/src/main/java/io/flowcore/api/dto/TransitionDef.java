package io.flowcore.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Definition of a state transition triggered by an event.
 *
 * @param id              unique transition identifier
 * @param from            source state name
 * @param to              target state name
 * @param trigger         the trigger that activates this transition, may be null
 * @param guardExpression SpEL or similar expression for conditional transitions, may be null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransitionDef(
        @NotBlank String id,
        @NotBlank String from,
        @NotBlank String to,
        TriggerDef trigger,
        String guardExpression
) {}
