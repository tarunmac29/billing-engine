package com.paycycle.billing_engine.infrastructure.idempotency;

import com.paycycle.billing_engine.domain.entity.IdempotencyKey;
import com.paycycle.billing_engine.domain.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * IdempotencyService — Double charge prevention ka engine.
 *
 * ============================================================
 * Flow:
 * ============================================================
 * 1. Request aati hai: X-Idempotency-Key: "client-uuid-123"
 *
 * 2. checkIdempotency() call karo:
 *    - Key hash compute karo
 *    - DB mein dhundho
 *    - Mila? → cached response return karo (REPLAY)
 *    - Nahi mila? → empty Optional (PROCEED)
 *
 * 3. Business logic execute karo
 *
 * 4. saveResponse() call karo:
 *    - Response ko DB mein save karo
 *    - Same transaction mein (business write ke saath)
 *
 * 5. Agar same key dobara aaye:
 *    - Step 2 mein mila → cached response return
 *    - Business logic NAHI chalega
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    private static final int TTL_HOURS = 24;

    /**
     * Idempotency check karo.
     * @return Optional with cached response if duplicate, empty if new request
     */
    @Transactional(readOnly = true)
    public Optional<String> checkIdempotency(
            String tenantId,
            String idempotencyKey,
            String operation) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty(); // key nahi hai toh skip
        }

        String keyHash = computeHash(tenantId + ":" + operation + ":" + idempotencyKey);

        return idempotencyKeyRepository.findByKeyHash(keyHash)
            .filter(ik -> !ik.isExpired())
            .map(ik -> {
                log.info("Idempotency HIT — returning cached response for key: {}",
                    idempotencyKey.substring(0, 8) + "...");
                return ik.getResponseSnapshot();
            });
    }

    /**
     * Response save karo — business write ke SAATH same transaction mein.
     */
    @Transactional
    public void saveResponse(
            String tenantId,
            String idempotencyKey,
            String operation,
            String responseJson,
            int httpStatus) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String keyHash = computeHash(tenantId + ":" + operation + ":" + idempotencyKey);

        // Already exists? Skip (race condition handle)
        if (idempotencyKeyRepository.findByKeyHash(keyHash).isPresent()) {
            return;
        }

        IdempotencyKey ik = IdempotencyKey.builder()
            .tenantId(tenantId)
            .keyHash(keyHash)
            .operation(operation)
            .httpStatus(httpStatus)
            .responseSnapshot(responseJson)
            .expiresAt(LocalDateTime.now().plusHours(TTL_HOURS))
            .build();

        idempotencyKeyRepository.save(ik);
        log.debug("Idempotency key saved for operation: {}", operation);
    }

    /**
     * SHA-256 hash compute karo.
     */
    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }
}
