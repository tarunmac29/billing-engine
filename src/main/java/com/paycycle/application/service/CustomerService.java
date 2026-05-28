package com.paycycle.billing_engine.application.service;

import com.paycycle.billing_engine.api.dto.request.CreateCustomerRequest;
import com.paycycle.billing_engine.api.dto.response.CustomerResponse;
import com.paycycle.billing_engine.domain.entity.Customer;
import com.paycycle.billing_engine.domain.entity.Tenant;
import com.paycycle.billing_engine.domain.repository.CustomerRepository;
import com.paycycle.billing_engine.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CustomerService — Customer register karo aur manage karo.
 *
 * Key rule: Ek tenant mein same email se 2 customers
 * nahi ban sakte. Ye check service layer mein hota hai
 * (DB unique constraint bhi hai as safety net).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public CustomerResponse createCustomer(String tenantId, CreateCustomerRequest request) {
        log.info("Creating customer '{}' for tenant: {}", request.getEmail(), tenantId);

        // Tenant exist karta hai?
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        // Duplicate email check
        if (customerRepository.existsByTenantIdAndEmail(tenantId, request.getEmail())) {
            throw new RuntimeException(
                "Customer already exists with email: " + request.getEmail()
            );
        }

        Customer customer = Customer.builder()
            .tenant(tenant)
            .name(request.getName())
            .email(request.getEmail().toLowerCase()) // email lowercase mein store karo
            .phone(request.getPhone())
            .isActive(true)
            .build();

        Customer saved = customerRepository.save(customer);
        log.info("Customer created: {}", saved.getId());
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomers(String tenantId, Pageable pageable) {
        return customerRepository
            .findByTenantIdAndIsActive(tenantId, true, pageable)
            .map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(String tenantId, String customerId) {
        Customer customer = customerRepository.findById(customerId)
            .filter(c -> c.getTenant().getId().equals(tenantId)) // tenant isolation
            .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));
        return CustomerResponse.from(customer);
    }
}
