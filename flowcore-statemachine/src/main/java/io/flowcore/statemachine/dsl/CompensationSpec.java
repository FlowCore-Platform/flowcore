package io.flowcore.statemachine.dsl;

import io.flowcore.api.dto.ActivityDef;
import io.flowcore.api.dto.CompensationDef;

/**
 * DSL value representing a compensation action for a workflow step.
 *
 * @param kind      "syncActivity" or "asyncActivity"
 * @param adapter   the provider adapter name
 * @param operation the operation name within the adapter
 */
public record CompensationSpec(
        String kind,
        String adapter,
        String operation
) {

    /**
     * Compact constructor with validation.
     */
    public CompensationSpec {
        if (!"syncActivity".equals(kind) && !"asyncActivity".equals(kind)) {
            throw new IllegalArgumentException(
                    "kind must be 'syncActivity' or 'asyncActivity', got: " + kind);
        }
    }

    /**
     * Convert this DSL value to an API {@link CompensationDef}.
     *
     * @return the corresponding CompensationDef
     */
    public CompensationDef toApiDto() {
        return new CompensationDef(kind, new ActivityDef(adapter, operation));
    }
}
