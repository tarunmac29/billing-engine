package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {

    // Tenant ke saath email dhundho — unique constraint match karta hai
    Optional<Customer> findByTenantIdAndEmail(String tenantId, String email);

    // Ek tenant ke saare active customers — paginated
    Page<Customer> findByTenantIdAndIsActive(String tenantId, Boolean isActive, Pageable pageable);

    // Email already exist karta hai is tenant mein?
    boolean existsByTenantIdAndEmail(String tenantId, String email);
}
