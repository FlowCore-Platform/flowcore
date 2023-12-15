package io.flowcore.api.dto;

/**
 * Definition of a compensation action for a workflow step.
 *
 * @param kind     the compensation kind (e.g. "undo", "notify", "manual")
 * @param activity the activity to execute for compensation
 */
public record CompensationDef(
        String kind,
        ActivityDef activity
) {}
