package com.paycycle.billing_engine.infrastructure.idempotency;

import com.paycycle.billing_engine.domain.entity.IdempotencyKey;
import com.paycycle.billing_engine.domain.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * IdempotencyServiceTest — Double charge prevention test karo.
 *
 * Most important tests:
 * 1. Same key dobara aaye toh cached response return ho
 * 2. New key aaye toh proceed karo
 * 3. Expired key treat karo as new request
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Tests")
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private static final String TENANT_ID = "tenant-123";
    private static final String OPERATION = "CREATE_INVOICE";

    @Test
    @DisplayName("Pehli request — empty Optional return hona chahiye (proceed karo)")
    void shouldReturnEmpty_whenFirstRequest() {
        // Arrange — key DB mein nahi hai
        when(idempotencyKeyRepository.findByKeyHash(any()))
            .thenReturn(Optional.empty());

        // Act
        Optional<String> result = idempotencyService
            .checkIdempotency(TENANT_ID, "unique-key-123", OPERATION);

        // Assert — empty matlab: new request, process karo
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Duplicate request — cached response return hona chahiye")
    void shouldReturnCachedResponse_whenDuplicateRequest() {
        // Arrange — same key DB mein hai
        IdempotencyKey existingKey = new IdempotencyKey();
        existingKey.setKeyHash("some-hash");
        existingKey.setOperation(OPERATION);
        existingKey.setResponseSnapshot("{\"id\":\"invoice-123\",\"status\":\"PAID\"}");
        existingKey.setHttpStatus(200);
        existingKey.setExpiresAt(LocalDateTime.now().plusHours(23)); // not expired

        when(idempotencyKeyRepository.findByKeyHash(any()))
            .thenReturn(Optional.of(existingKey));

        // Act
        Optional<String> result = idempotencyService
            .checkIdempotency(TENANT_ID, "same-key-456", OPERATION);

        // Assert — cached response mila!
        assertThat(result).isPresent();
        assertThat(result.get()).contains("invoice-123");
        assertThat(result.get()).contains("PAID");
    }

    @Test
    @DisplayName("Expired key — empty return hona chahiye (re-process karo)")
    void shouldReturnEmpty_whenKeyExpired() {
        // Arrange — key expire ho gayi
        IdempotencyKey expiredKey = new IdempotencyKey();
        expiredKey.setKeyHash("some-hash");
        expiredKey.setResponseSnapshot("{\"id\":\"old-invoice\"}");
        expiredKey.setExpiresAt(LocalDateTime.now().minusHours(1)); // EXPIRED!

        when(idempotencyKeyRepository.findByKeyHash(any()))
            .thenReturn(Optional.of(expiredKey));

        // Act
        Optional<String> result = idempotencyService
            .checkIdempotency(TENANT_ID, "expired-key", OPERATION);

        // Assert — expired key ignore ho gayi
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Null key — skip idempotency check (optional feature)")
    void shouldReturnEmpty_whenKeyIsNull() {
        // Act
        Optional<String> result = idempotencyService
            .checkIdempotency(TENANT_ID, null, OPERATION);

        // Assert — null key pe DB call nahi hona chahiye
        assertThat(result).isEmpty();
        verify(idempotencyKeyRepository, never()).findByKeyHash(any());
    }

    @Test
    @DisplayName("saveResponse — response DB mein save hona chahiye")
    void shouldSaveResponse_successfully() {
        // Arrange
        when(idempotencyKeyRepository.findByKeyHash(any()))
            .thenReturn(Optional.empty()); // naya key

        // Act
        idempotencyService.saveResponse(
            TENANT_ID,
            "new-key-789",
            OPERATION,
            "{\"id\":\"invoice-456\"}",
            201
        );

        // Assert — save call hua?
        verify(idempotencyKeyRepository, times(1))
            .save(argThat(key ->
                key.getOperation().equals(OPERATION) &&
                key.getHttpStatus() == 201 &&
                key.getResponseSnapshot().contains("invoice-456")
            ));
    }

    @Test
    @DisplayName("saveResponse — duplicate key already exists toh dobara save nahi")
    void shouldNotSave_whenKeyAlreadyExists() {
        // Arrange — key already exists
        IdempotencyKey existingKey = new IdempotencyKey();
        existingKey.setKeyHash("some-hash");
        existingKey.setExpiresAt(LocalDateTime.now().plusHours(23));

        when(idempotencyKeyRepository.findByKeyHash(any()))
            .thenReturn(Optional.of(existingKey));

        // Act
        idempotencyService.saveResponse(
            TENANT_ID, "existing-key", OPERATION, "{}", 200);

        // Assert — save nahi hua (already exists)
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Alag tenants ki same key — alag honi chahiye")
    void shouldTreatSameKeyDifferently_forDifferentTenants() {
        // Arrange
        when(idempotencyKeyRepository.findByKeyHash(any()))
            .thenReturn(Optional.empty());

        // Act — same key, alag tenants
        Optional<String> result1 = idempotencyService
            .checkIdempotency("tenant-A", "same-key", OPERATION);
        Optional<String> result2 = idempotencyService
            .checkIdempotency("tenant-B", "same-key", OPERATION);

        // Assert — dono empty (new requests)
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();

        // Verify — DB mein alag hashes se dhundha
        // (tenant-A + key aur tenant-B + key ke hashes alag hote hain)
        verify(idempotencyKeyRepository, times(2)).findByKeyHash(any());
    }
}
