package com.paycycle.billing_engine.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * OUTBOX_EVENT — Transactional Outbox Pattern
 *
 * ============================================================
 * Problem jo solve karta hai:
 * ============================================================
 * Invoice PAID hua → Kafka ko event bhejna tha
 * Usi moment server crash ho gaya
 * Result: DB mein paid hai, Kafka ko pata nahi!
 *
 * Solution — Outbox Pattern:
 * 1. Invoice save karo (DB transaction)
 * 2. SAME transaction mein outbox_event table mein bhi likho
 * 3. Agar crash hua → dono rollback — consistent state
 * 4. Scheduler har 5 second mein outbox check karta hai
 * 5. Unpublished events Kafka par bhejta hai
 * 6. Published = true mark karta hai
 * ============================================================
 *
 * Guarantee: At-least-once delivery (Kafka mein duplicate
 * aa sakta hai — consumers idempotent hone chahiye)
 */
@Entity
@Table(name = "outbox_event")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    // Kaun sa entity — SUBSCRIPTION, INVOICE, PAYMENT
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    // Us entity ka ID
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    // Event ka naam — invoice.paid, subscription.cancelled
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // Kafka topic
    @Column(name = "kafka_topic", nullable = false, length = 200)
    private String kafkaTopic;

    // Event data JSON format mein
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    // Kafka par bheja ki nahi?
    @Column(name = "published", nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.retryCount = this.retryCount + 1;
        this.errorMessage = error;
    }
}
