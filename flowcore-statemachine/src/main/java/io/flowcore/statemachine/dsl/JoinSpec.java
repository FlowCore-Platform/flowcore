package io.flowcore.statemachine.dsl;

import java.util.List;

/**
 * DSL value representing a join specification (parallel branch convergence).
 *
 * @param joinStepId  the step identifier that waits for branches
 * @param forkStepId  the fork step this join is paired with
 * @param branchNames logical names for each parallel branch
 * @param joinPolicy  "ALL" or "ANY"
 */
public record JoinSpec(
        String joinStepId,
        String forkStepId,
        List<String> branchNames,
        String joinPolicy
) {

    /**
     * Compact constructor with validation.
     */
    public JoinSpec {
        if (!"ALL".equals(joinPolicy) && !"ANY".equals(joinPolicy)) {
            throw new IllegalArgumentException(
                    "joinPolicy must be 'ALL' or 'ANY', got: " + joinPolicy);
        }
    }
}
