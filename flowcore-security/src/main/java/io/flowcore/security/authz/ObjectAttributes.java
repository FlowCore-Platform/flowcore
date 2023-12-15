package io.flowcore.security.authz;

/**
 * Represents attributes of the object being accessed, used for ABAC evaluation.
 *
 * @param objectType the type of object (e.g. "workflow", "payment")
 * @param objectId   the unique identifier of the object
 * @param tenantId   the tenant that owns the object
 * @param metadata   additional key-value metadata about the object
 */
public record ObjectAttributes(
        String objectType,
        String objectId,
        String tenantId,
        java.util.Map<String, String> metadata
) {

    /**
     * Compact constructor ensuring a defensive copy of metadata.
     */
    public ObjectAttributes {
        metadata = metadata != null ? java.util.Map.copyOf(metadata) : java.util.Map.of();
    }
}
