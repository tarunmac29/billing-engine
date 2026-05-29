package com.paycycle.billing_engine.api.dto.response;

import com.paycycle.billing_engine.domain.entity.Plan;
import com.paycycle.billing_engine.domain.enums.BillingInterval;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PlanResponse — API response format.
 *
 * Static factory method `from(Plan)` use karo —
 * entity ko response mein convert karta hai.
 * Ye "Factory Method" design pattern hai.
 */
@Data
@Builder
public class PlanResponse {

    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private BillingInterval billingInterval;
    private Integer trialDays;
    private Integer gracePeriodDays;
    private Integer maxRetryCount;
    private Boolean isActive;
    private LocalDateTime createdAt;

    // Factory method — Plan entity → PlanResponse
    public static PlanResponse from(Plan plan) {
        return PlanResponse.builder()
            .id(plan.getId())
            .name(plan.getName())
            .description(plan.getDescription())
            .price(plan.getPrice())
            .currency(plan.getCurrency())
            .billingInterval(plan.getBillingInterval())
            .trialDays(plan.getTrialDays())
            .gracePeriodDays(plan.getGracePeriodDays())
            .maxRetryCount(plan.getMaxRetryCount())
            .isActive(plan.getIsActive())
            .createdAt(plan.getCreatedAt())
            .build();
    }
}
