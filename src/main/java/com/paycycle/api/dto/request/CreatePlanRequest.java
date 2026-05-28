// =====================================================
// FILE: CreatePlanRequest.java
// =====================================================
package com.paycycle.billing_engine.api.dto.request;

import com.paycycle.billing_engine.domain.enums.BillingInterval;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO = Data Transfer Object
 * Controller → Service tak data le jaata hai.
 *
 * Entity aur DTO ALAG rakho — entity DB se baat karta hai,
 * DTO API se baat karta hai. Dono mix mat karo.
 *
 * @NotBlank — empty string allowed nahi
 * @DecimalMin — minimum value check
 * @NotNull — null allowed nahi
 */
@Data
public class CreatePlanRequest {

    @NotBlank(message = "Plan name required hai")
    @Size(max = 100, message = "Name 100 characters se zyada nahi ho sakta")
    private String name;

    private String description;

    @NotNull(message = "Price required hai")
    @DecimalMin(value = "0.01", message = "Price 0 se zyada hona chahiye")
    private BigDecimal price;

    @Size(min = 3, max = 3, message = "Currency 3 characters ki honi chahiye (e.g. USD, INR)")
    private String currency;

    @NotNull(message = "Billing interval required hai")
    private BillingInterval billingInterval;

    @Min(value = 0, message = "Trial days 0 ya zyada hone chahiye")
    private Integer trialDays;

    @Min(value = 0)
    private Integer gracePeriodDays;

    @Min(value = 1) @Max(value = 10)
    private Integer maxRetryCount;
}
