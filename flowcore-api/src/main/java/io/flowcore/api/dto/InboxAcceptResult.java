package io.flowcore.api.dto;

import java.util.UUID;

/**
 * Result of attempting to accept a message into the inbox.
 */
public record InboxAcceptResult(boolean accepted, UUID id) {}
