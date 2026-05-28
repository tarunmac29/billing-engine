package com.paycycle.billing_engine.api.controller;

import com.paycycle.billing_engine.api.dto.request.CreateCustomerRequest;
import com.paycycle.billing_engine.api.dto.response.CustomerResponse;
import com.paycycle.billing_engine.application.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CustomerController
 *
 * POST   /v1/tenants/{tenantId}/customers          → Customer banao
 * GET    /v1/tenants/{tenantId}/customers          → List (paginated)
 * GET    /v1/tenants/{tenantId}/customers/{id}     → Ek customer
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(customerService.createCustomer(tenantId, request));
    }

    // Pagination support: ?page=0&size=20&sort=createdAt,desc
    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getCustomers(
            @PathVariable String tenantId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(customerService.getCustomers(tenantId, pageable));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomer(
            @PathVariable String tenantId,
            @PathVariable String customerId) {
        return ResponseEntity.ok(customerService.getCustomer(tenantId, customerId));
    }
}
