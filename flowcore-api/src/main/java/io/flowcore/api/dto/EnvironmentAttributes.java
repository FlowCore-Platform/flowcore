package io.flowcore.api.dto;

/**
 * Environment attributes for ABAC evaluation.
 */
public record EnvironmentAttributes(
    String ip,
    String time,
    Double riskScore
) {}
