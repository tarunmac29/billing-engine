package com.paycycle.billing_engine.api.dto.response;

import com.paycycle.billing_engine.domain.entity.Customer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponse {

    private String id;
    private String name;
    private String email;
    private String phone;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static CustomerResponse from(Customer customer) {
        return CustomerResponse.builder()
            .id(customer.getId())
            .name(customer.getName())
            .email(customer.getEmail())
            .phone(customer.getPhone())
            .isActive(customer.getIsActive())
            .createdAt(customer.getCreatedAt())
            .build();
    }
}
