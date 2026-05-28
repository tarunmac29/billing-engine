package com.paycycle.billing_engine.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCustomerRequest {

    @NotBlank(message = "Name required hai")
    @Size(max = 100)
    private String name;

    @Email(message = "Valid email chahiye")
    @NotBlank(message = "Email required hai")
    private String email;

    @Size(max = 30)
    private String phone;
}
