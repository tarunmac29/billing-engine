package com.paycycle.billing_engine.domain.entity;

import com.paycycle.billing_engine.domain.enums.BillingInterval;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

/**
 * PLAN — Subscription products.
 *
 * Example plans:
 *   - "Basic Monthly"  → Rs 99/month,  0 trial days
 *   - "Pro Yearly"     → Rs 999/year,  14 trial days
 *   - "Enterprise"     → Rs 9999/month, 30 trial days
 *
 * IMPORTANT: Plan kabhi DELETE mat karo — sirf is_active = false karo.
 * Kyunki existing subscriptions us plan ko reference karti hain.
 * Agar plan delete hua toh foreign key constraint fail hoga.
 * Ye "Soft Delete" pattern hai.
 */
@Entity
@Table(name = "plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // BigDecimal use karo — NEVER double/float for money!
    // double: 0.1 + 0.2 = 0.30000000000000004 (WRONG!)
    // BigDecimal: 0.1 + 0.2 = 0.3 (CORRECT!)
    @DecimalMin("0.00")
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    // ENUM database mein STRING store hoga (e.g. "MONTHLY")
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval billingInterval;

    @Column(name = "trial_days", nullable = false)
    @Builder.Default
    private Integer trialDays = 0;

    @Column(name = "grace_period_days", nullable = false)
    @Builder.Default
    private Integer gracePeriodDays = 3;

    @Column(name = "max_retry_count", nullable = false)
    @Builder.Default
    private Integer maxRetryCount = 3;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
