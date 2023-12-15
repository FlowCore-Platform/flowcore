package io.flowcore.tx.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link InboxMessageEntity}.
 */
public interface InboxMessageRepository extends JpaRepository<InboxMessageEntity, UUID> {

    /**
     * Checks whether a message from the given source with the given
     * message ID has already been consumed by the specified consumer group.
     *
     * @param source        the message source system or topic
     * @param messageId     the unique message identifier
     * @param consumerGroup the consumer group scope
     * @return {@code true} if the message was already processed
     */
    boolean existsBySourceAndMessageIdAndConsumerGroup(String source, String messageId, String consumerGroup);
}
