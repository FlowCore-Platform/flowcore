package io.flowcore.tx.inbox;

import io.flowcore.api.InboxService;
import io.flowcore.api.dto.InboxAcceptResult;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Default implementation of {@link InboxService}.
 * <p>
 * Uses a unique constraint on {@code (source, messageId, consumerGroup)}
 * to guarantee at-most-once processing per consumer group. When a duplicate
 * insertion is detected via {@link DataIntegrityViolationException}, the
 * message is rejected as already accepted.
 */
@Service
public class DefaultInboxService implements InboxService {

    private static final Logger log = LoggerFactory.getLogger(DefaultInboxService.class);

    private final InboxMessageRepository repository;

    public DefaultInboxService(InboxMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public InboxAcceptResult tryAccept(String source, String messageId, String consumerGroup) {
        InboxMessageEntity entity = InboxMessageEntity.builder()
                .id(UUID.randomUUID())
                .source(source)
                .messageId(messageId)
                .consumerGroup(consumerGroup)
                .receivedAt(Instant.now())
                .build();

        try {
            repository.saveAndFlush(entity);
            log.debug("Accepted inbox message: source={}, messageId={}, consumerGroup={}", source, messageId, consumerGroup);
            return new InboxAcceptResult(true, entity.getId());
        } catch (DataIntegrityViolationException ex) {
            log.debug("Inbox message already exists: source={}, messageId={}, consumerGroup={}", source, messageId, consumerGroup);
            return new InboxAcceptResult(false, null);
        }
    }
}
