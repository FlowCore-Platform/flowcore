package io.flowcore.api;

import io.flowcore.api.dto.InboxAcceptResult;

/**
 * Inbox pattern service for exactly-once message processing via deduplication.
 */
public interface InboxService {

    /**
     * Attempts to accept an incoming message for a consumer group.
     * If the message was already processed, it returns a rejection (accepted=false).
     *
     * @param source        the source system or topic that produced the message
     * @param messageId     the unique message identifier from the source
     * @param consumerGroup the logical consumer group deduplication scope
     * @return the result indicating whether the message was newly accepted
     */
    InboxAcceptResult tryAccept(String source, String messageId, String consumerGroup);
}
