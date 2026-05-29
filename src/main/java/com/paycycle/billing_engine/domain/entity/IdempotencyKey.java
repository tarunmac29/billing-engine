package com.paycycle.billing_engine.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * IDEMPOTENCY_KEY — Double charge prevention
 *
 * ============================================================
 * Problem jo solve karta hai:
 * ============================================================
 * Client ne POST /charge bheja
 * Network timeout hua — client ko response nahi mila
 * Client ne dobara bheja (retry)
 * Dono requests server tak pahunchi
 * Result: DOUBLE CHARGE! Customer ka 2x paisa kata!
 *
 * Solution — Idempotency Key:
 * 1. Client har request ke saath unique key bhejta hai
 *    Header: X-Idempotency-Key: uuid-123
 * 2. Server pehli baar: process karo, response save karo
 * 3. Server doosri baar same key: saved response return karo
 *    Processing dobara NAHI hogi
 * ============================================================
 *
 * Key hash = SHA-256(idempotency_key + operation + tenant_id)
 * Unique constraint DB level par bhi hai — last resort safety
 */
@Entity
@Table(name = "idempotency_key")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    // SHA-256 hash — DB mein raw key store nahi hoti
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(name = "operation", nullable = false, length = 100)
    private String operation;

    // Cached HTTP status code
    @Column(name = "http_status")
    private Integer httpStatus;

    // Pura response JSON — replay ke liye
    @Column(name = "response_snapshot", columnDefinition = "JSON")
    private String responseSnapshot;

    // Request body ka hash — agar same key pe alag body aaye toh detect karo
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    // Kab expire hoga ye key
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
