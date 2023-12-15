package io.flowcore.tx.idempotency;

import io.flowcore.api.dto.StoredResponse;
import io.flowcore.tx.config.TransactionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultIdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private TransactionProperties properties;
    private DefaultIdempotencyService service;

    @BeforeEach
    void setUp() {
        properties = new TransactionProperties();
        service = new DefaultIdempotencyService(repository, properties);
    }

    // ---- Helper ----

    private IdempotencyKeyEntity buildEntity(String scope, String key,
                                              String requestHash, String responseJson,
                                              Instant expiresAt) {
        return IdempotencyKeyEntity.builder()
                .scope(scope)
                .key(key)
                .requestHash(requestHash)
                .responseJson(responseJson)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }

    // ========================================================================
    // find()
    // ========================================================================

    @Nested
    @DisplayName("find")
    class FindTests {

        @Test
        @DisplayName("should return empty when key is not found")
        void shouldReturnEmptyWhenNotFound() {
            when(repository.findByScopeAndKey("order", "key-1")).thenReturn(Optional.empty());

            Optional<StoredResponse> result = service.find("order", "key-1", "hash-abc");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when key has expired")
        void shouldReturnEmptyWhenExpired() {
            Instant pastExpiry = Instant.now().minusSeconds(3600);
            IdempotencyKeyEntity entity = buildEntity(
                    "order", "key-2", "hash-abc", "{\"status\":\"ok\"}", pastExpiry);

            when(repository.findByScopeAndKey("order", "key-2")).thenReturn(Optional.of(entity));

            Optional<StoredResponse> result = service.find("order", "key-2", "hash-abc");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when request hash does not match")
        void shouldReturnEmptyWhenHashMismatch() {
            Instant futureExpiry = Instant.now().plusSeconds(3600);
            IdempotencyKeyEntity entity = buildEntity(
                    "order", "key-3", "hash-original", "{\"status\":\"ok\"}", futureExpiry);

            when(repository.findByScopeAndKey("order", "key-3")).thenReturn(Optional.of(entity));

            Optional<StoredResponse> result = service.find("order", "key-3", "hash-different");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return stored response when found and not expired and hash matches")
        void shouldReturnResponseWhenFoundAndValid() {
            Instant futureExpiry = Instant.now().plusSeconds(3600);
            IdempotencyKeyEntity entity = buildEntity(
                    "order", "key-4", "hash-abc", "{\"orderId\":\"123\"}", futureExpiry);

            when(repository.findByScopeAndKey("order", "key-4")).thenReturn(Optional.of(entity));

            Optional<StoredResponse> result = service.find("order", "key-4", "hash-abc");

            assertThat(result).isPresent();
            StoredResponse response = result.get();
            assertThat(response.scope()).isEqualTo("order");
            assertThat(response.key()).isEqualTo("key-4");
            assertThat(response.requestHash()).isEqualTo("hash-abc");
            assertThat(response.responseJson()).isEqualTo("{\"orderId\":\"123\"}");
        }

        @Test
        @DisplayName("should return response when requestHash in entity is null")
        void shouldReturnResponseWhenEntityHashIsNull() {
            Instant futureExpiry = Instant.now().plusSeconds(3600);
            IdempotencyKeyEntity entity = buildEntity(
                    "order", "key-5", null, "{\"ok\":true}", futureExpiry);

            when(repository.findByScopeAndKey("order", "key-5")).thenReturn(Optional.of(entity));

            Optional<StoredResponse> result = service.find("order", "key-5", "any-hash");

            assertThat(result).isPresent();
            assertThat(result.get().responseJson()).isEqualTo("{\"ok\":true}");
        }

        @Test
        @DisplayName("should return response when expiresAt is null (never expires)")
        void shouldReturnResponseWhenNoExpiry() {
            IdempotencyKeyEntity entity = buildEntity(
                    "order", "key-6", "hash-xyz", "{\"result\":1}", null);

            when(repository.findByScopeAndKey("order", "key-6")).thenReturn(Optional.of(entity));

            Optional<StoredResponse> result = service.find("order", "key-6", "hash-xyz");

            assertThat(result).isPresent();
            assertThat(result.get().responseJson()).isEqualTo("{\"result\":1}");
        }
    }

    // ========================================================================
    // store()
    // ========================================================================

    @Nested
    @DisplayName("store")
    class StoreTests {

        @Test
        @DisplayName("should save entity and return stored response on success")
        void shouldSaveAndReturnResponse() {
            when(repository.saveAndFlush(any(IdempotencyKeyEntity.class))).thenReturn(null);

            Instant expiresAt = Instant.now().plusSeconds(600);
            StoredResponse result = service.store(
                    "payment", "pay-1", "hash-123", "{\"paid\":true}", expiresAt);

            assertThat(result.scope()).isEqualTo("payment");
            assertThat(result.key()).isEqualTo("pay-1");
            assertThat(result.requestHash()).isEqualTo("hash-123");
            assertThat(result.responseJson()).isEqualTo("{\"paid\":true}");
            verify(repository).saveAndFlush(any(IdempotencyKeyEntity.class));
        }

        @Test
        @DisplayName("should return existing entry on DataIntegrityViolation (conflict)")
        void shouldReturnExistingOnConflict() {
            IdempotencyKeyEntity existing = buildEntity(
                    "payment", "pay-2", "hash-existing", "{\"paid\":false}", null);

            when(repository.saveAndFlush(any(IdempotencyKeyEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("unique constraint"));
            when(repository.findByScopeAndKey("payment", "pay-2"))
                    .thenReturn(Optional.of(existing));

            StoredResponse result = service.store(
                    "payment", "pay-2", "hash-new", "{\"paid\":true}", null);

            assertThat(result.scope()).isEqualTo("payment");
            assertThat(result.key()).isEqualTo("pay-2");
            assertThat(result.requestHash()).isEqualTo("hash-existing");
            assertThat(result.responseJson()).isEqualTo("{\"paid\":false}");
        }

        @Test
        @DisplayName("should rethrow DataIntegrityViolation when existing entry not found after conflict")
        void shouldRethrowWhenNoExistingFound() {
            when(repository.saveAndFlush(any(IdempotencyKeyEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("unique constraint"));
            when(repository.findByScopeAndKey("payment", "pay-3"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.store(
                    "payment", "pay-3", "hash-xyz", "{}", null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("should store with null expiresAt")
        void shouldStoreWithNullExpiresAt() {
            when(repository.saveAndFlush(any(IdempotencyKeyEntity.class))).thenReturn(null);

            StoredResponse result = service.store(
                    "order", "ord-1", "h1", "{\"ok\":true}", null);

            assertThat(result.scope()).isEqualTo("order");
            assertThat(result.key()).isEqualTo("ord-1");
            verify(repository).saveAndFlush(any(IdempotencyKeyEntity.class));
        }
    }

    // ========================================================================
    // cleanupExpiredKeys()
    // ========================================================================

    @Nested
    @DisplayName("cleanupExpiredKeys")
    class CleanupTests {

        @Test
        @DisplayName("should delete expired keys via repository")
        void shouldDeleteExpiredKeys() {
            service.cleanupExpiredKeys();

            verify(repository).deleteByExpiresAtBefore(any(Instant.class));
        }

        @Test
        @DisplayName("should swallow exceptions during cleanup")
        void shouldSwallowExceptions() {
            // Should not throw even if repository throws
            doThrow(new RuntimeException("DB connection lost"))
                    .when(repository).deleteByExpiresAtBefore(any(Instant.class));

            // Must not throw
            service.cleanupExpiredKeys();
        }
    }
}
