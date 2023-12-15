package io.flowcore.tx.inbox;

import io.flowcore.api.dto.InboxAcceptResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultInboxServiceTest {

    @Mock
    private InboxMessageRepository repository;

    private DefaultInboxService service;

    @BeforeEach
    void setUp() {
        service = new DefaultInboxService(repository);
    }

    @Nested
    @DisplayName("tryAccept")
    class TryAcceptTests {

        @Test
        @DisplayName("should accept new message and return accepted=true with id")
        void shouldAcceptNewMessage() {
            ArgumentCaptor<InboxMessageEntity> captor = ArgumentCaptor.forClass(InboxMessageEntity.class);
            when(repository.saveAndFlush(captor.capture())).thenReturn(null);

            InboxAcceptResult result = service.tryAccept("kafka", "msg-001", "order-processor");

            assertThat(result.accepted()).isTrue();
            assertThat(result.id()).isNotNull();

            InboxMessageEntity saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(result.id());
            assertThat(saved.getSource()).isEqualTo("kafka");
            assertThat(saved.getMessageId()).isEqualTo("msg-001");
            assertThat(saved.getConsumerGroup()).isEqualTo("order-processor");
            assertThat(saved.getReceivedAt()).isNotNull();
        }

        @Test
        @DisplayName("should reject duplicate message and return accepted=false")
        void shouldRejectDuplicate() {
            when(repository.saveAndFlush(any(InboxMessageEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            InboxAcceptResult result = service.tryAccept("kafka", "msg-001", "order-processor");

            assertThat(result.accepted()).isFalse();
            assertThat(result.id()).isNull();
        }

        @Test
        @DisplayName("should allow same messageId across different consumer groups")
        void shouldAllowSameMessageInDifferentConsumerGroups() {
            ArgumentCaptor<InboxMessageEntity> captor = ArgumentCaptor.forClass(InboxMessageEntity.class);
            when(repository.saveAndFlush(captor.capture())).thenReturn(null);

            InboxAcceptResult result1 = service.tryAccept("kafka", "msg-002", "order-processor");
            InboxAcceptResult result2 = service.tryAccept("kafka", "msg-002", "notification-service");

            assertThat(result1.accepted()).isTrue();
            assertThat(result2.accepted()).isTrue();
            assertThat(result1.id()).isNotEqualTo(result2.id());
        }

        @Test
        @DisplayName("should allow same messageId across different sources")
        void shouldAllowSameMessageFromDifferentSources() {
            ArgumentCaptor<InboxMessageEntity> captor = ArgumentCaptor.forClass(InboxMessageEntity.class);
            when(repository.saveAndFlush(captor.capture())).thenReturn(null);

            InboxAcceptResult result1 = service.tryAccept("kafka", "msg-003", "processor");
            InboxAcceptResult result2 = service.tryAccept("rabbitmq", "msg-003", "processor");

            assertThat(result1.accepted()).isTrue();
            assertThat(result2.accepted()).isTrue();
            assertThat(result1.id()).isNotEqualTo(result2.id());
        }
    }
}
