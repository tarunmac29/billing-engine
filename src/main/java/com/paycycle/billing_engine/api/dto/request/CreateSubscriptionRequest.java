package com.paycycle.billing_engine.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSubscriptionRequest {

    @NotBlank(message = "customerId required hai")
    private String customerId;

    @NotBlank(message = "planId required hai")
    private String planId;
}
