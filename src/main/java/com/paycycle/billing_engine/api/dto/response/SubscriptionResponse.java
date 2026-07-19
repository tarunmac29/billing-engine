package com.paycycle.billing_engine.api.dto.response;

import com.paycycle.billing_engine.domain.entity.Subscription;
import com.paycycle.billing_engine.domain.enums.SubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SubscriptionResponse {

    private String id;
    private String customerId;
    private String customerName;
    private String planId;
    private String planName;
    private SubscriptionStatus status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime trialEnd;
    private Integer retryCount;
    private LocalDateTime createdAt;

    public static SubscriptionResponse from(Subscription s) {
        return SubscriptionResponse.builder()
            .id(s.getId())
            .customerId(s.getCustomer().getId())
            .customerName(s.getCustomer().getName())
            .planId(s.getPlan().getId())
            .planName(s.getPlan().getName())
            .status(s.getStatus())
            .currentPeriodStart(s.getCurrentPeriodStart())
            .currentPeriodEnd(s.getCurrentPeriodEnd())
            .trialEnd(s.getTrialEnd())
            .retryCount(s.getRetryCount())
            .createdAt(s.getCreatedAt())
            .build();
    }
}
