package io.flowcore.statemachine.dsl;

import java.util.List;

/**
 * DSL value representing a fork specification (parallel branch start).
 *
 * @param forkStepId  the step identifier that initiates the fork
 * @param branchNames logical names for each parallel branch
 */
public record ForkSpec(
        String forkStepId,
        List<String> branchNames
) {}
