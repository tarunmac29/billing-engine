package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByKeyHash(String keyHash);

    // Expired keys cleanup ke liye
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
