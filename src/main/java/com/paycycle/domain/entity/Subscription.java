package com.paycycle.billing_engine.domain.entity;

import com.paycycle.billing_engine.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * SUBSCRIPTION — Ye poore project ka CORE entity hai.
 *
 * Ye ek customer aur ek plan ko jodata hai.
 * Billing engine isi entity ko dekhta hai aur decide karta hai:
 *   "Kab charge karna hai? Kitna? Retry karna hai ya nahi?"
 *
 * ============================================================
 * @Version — OPTIMISTIC LOCKING ka jadoo
 * ============================================================
 * Problem: 2 threads ek saath same subscription update karein
 *
 * Thread A padhta hai: version=0, status=ACTIVE
 * Thread B padhta hai: version=0, status=ACTIVE
 * Thread A likhta hai: version=1, status=PAST_DUE  ✅
 * Thread B likhta hai: version=1 par DB mein hai 1!
 *   → OptimisticLockException! Thread B retry karega ✅
 *
 * Bina @Version ke dono writes ho jaate — data corrupt!
 * ============================================================
 *
 * State Machine transitions (guarded in SubscriptionService):
 *   TRIALING  → ACTIVE      (trial khatam, first payment)
 *   TRIALING  → EXPIRED     (trial khatam, no payment)
 *   ACTIVE    → PAST_DUE    (payment fail)
 *   ACTIVE    → PAUSED      (customer ne pause kiya)
 *   ACTIVE    → CANCELLED   (customer ne cancel kiya)
 *   PAST_DUE  → ACTIVE      (retry successful)
 *   PAST_DUE  → CANCELLED   (max retries exhausted)
 *   PAUSED    → ACTIVE      (customer ne resume kiya)
 */
@Entity
@Table(name = "subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIALING;

    @Column(name = "current_period_start", nullable = false)
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDateTime currentPeriodEnd;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private Boolean cancelAtPeriodEnd = false;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    // ============================================================
    // @Version — OPTIMISTIC LOCKING
    // JPA automatically is field ko check karta hai har update par
    // KABHI manually set mat karna!
    // ============================================================
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ============================================================
    // Domain Methods — Business logic entity ke andar
    // Service layer sirf inhe call karta hai
    // ============================================================

    /** Kya ye subscription abhi active billing mein hai? */
    public boolean isCurrentlyBillable() {
        return this.status == SubscriptionStatus.ACTIVE
            || this.status == SubscriptionStatus.PAST_DUE;
    }

    /** Trial khatam hua ki nahi? */
    public boolean isTrialExpired() {
        return this.trialEnd != null
            && LocalDateTime.now().isAfter(this.trialEnd);
    }

    /** Billing period khatam hua ki nahi? */
    public boolean isPeriodExpired() {
        return LocalDateTime.now().isAfter(this.currentPeriodEnd);
    }

    /** TRIALING → ACTIVE transition */
    public void activate(LocalDateTime newPeriodStart, LocalDateTime newPeriodEnd) {
        validateTransition(SubscriptionStatus.ACTIVE);
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStart = newPeriodStart;
        this.currentPeriodEnd = newPeriodEnd;
        this.retryCount = 0;
        this.nextRetryAt = null;
    }

    /** ACTIVE → PAST_DUE transition */
    public void markPastDue(LocalDateTime nextRetry) {
        validateTransition(SubscriptionStatus.PAST_DUE);
        this.status = SubscriptionStatus.PAST_DUE;
        this.retryCount = this.retryCount + 1;
        this.nextRetryAt = nextRetry;
    }

    /** Any → CANCELLED transition */
    public void cancel(boolean immediately) {
        if (immediately) {
            this.status = SubscriptionStatus.CANCELLED;
            this.cancelledAt = LocalDateTime.now();
        } else {
            // Period khatam hone par cancel hoga
            this.cancelAtPeriodEnd = true;
        }
    }

    /** ACTIVE → PAUSED transition */
    public void pause() {
        validateTransition(SubscriptionStatus.PAUSED);
        this.status = SubscriptionStatus.PAUSED;
    }

    /** PAUSED → ACTIVE transition */
    public void resume(LocalDateTime newPeriodEnd) {
        if (this.status != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException(
                "Sirf PAUSED subscription resume ho sakti hai. Current: " + this.status
            );
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodEnd = newPeriodEnd;
    }

    // Guard — invalid transitions rokta hai
    private void validateTransition(SubscriptionStatus target) {
        boolean valid = switch (target) {
            case ACTIVE    -> status == SubscriptionStatus.TRIALING
                           || status == SubscriptionStatus.PAST_DUE;
            case PAST_DUE  -> status == SubscriptionStatus.ACTIVE;
            case PAUSED    -> status == SubscriptionStatus.ACTIVE;
            case CANCELLED -> true; // kisi bhi state se cancel ho sakta hai
            case EXPIRED   -> status == SubscriptionStatus.TRIALING;
            default        -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                "Invalid transition: " + this.status + " → " + target
            );
        }
    }
}
