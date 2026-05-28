// =====================================================
// FILE: PlanRepository.java
// =====================================================
package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, String> {

    // Tenant ke saare active plans
    List<Plan> findByTenantIdAndIsActive(String tenantId, Boolean isActive);

    // Specific plan — tenant ke andar (security: dusre tenant ka plan nahi dekh sakta)
    Optional<Plan> findByIdAndTenantId(String id, String tenantId);
}
