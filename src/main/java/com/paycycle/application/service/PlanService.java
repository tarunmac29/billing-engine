package com.paycycle.billing_engine.application.service;

import com.paycycle.billing_engine.api.dto.request.CreatePlanRequest;
import com.paycycle.billing_engine.api.dto.response.PlanResponse;
import com.paycycle.billing_engine.domain.entity.Plan;
import com.paycycle.billing_engine.domain.entity.Tenant;
import com.paycycle.billing_engine.domain.repository.PlanRepository;
import com.paycycle.billing_engine.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PlanService — Plan create karo, dekho, band karo.
 *
 * @Transactional matlab: agar beech mein kuch fail hua
 * toh poora operation rollback ho jaayega.
 * Half-save kabhi nahi hoga.
 *
 * @Slf4j — Lombok se @Log4j automatically inject hota hai.
 * log.info(), log.error() direct use karo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public PlanResponse createPlan(String tenantId, CreatePlanRequest request) {
        log.info("Creating plan '{}' for tenant: {}", request.getName(), tenantId);

        // Tenant exist karta hai?
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));

        // Plan entity banao — Builder pattern
        Plan plan = Plan.builder()
            .tenant(tenant)
            .name(request.getName())
            .description(request.getDescription())
            .price(request.getPrice())
            .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
            .billingInterval(request.getBillingInterval())
            .trialDays(request.getTrialDays() != null ? request.getTrialDays() : 0)
            .gracePeriodDays(request.getGracePeriodDays() != null ? request.getGracePeriodDays() : 3)
            .maxRetryCount(request.getMaxRetryCount() != null ? request.getMaxRetryCount() : 3)
            .isActive(true)
            .build();

        Plan saved = planRepository.save(plan);
        log.info("Plan created successfully with id: {}", saved.getId());

        return PlanResponse.from(saved);
    }

    @Transactional(readOnly = true) // readOnly = true → performance optimization
    public List<PlanResponse> getActivePlans(String tenantId) {
        return planRepository.findByTenantIdAndIsActive(tenantId, true)
            .stream()
            .map(PlanResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(String tenantId, String planId) {
        Plan plan = planRepository.findByIdAndTenantId(planId, tenantId)
            .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        return PlanResponse.from(plan);
    }

    // Soft delete — delete nahi karte, sirf band karte hain
    @Transactional
    public void deactivatePlan(String tenantId, String planId) {
        Plan plan = planRepository.findByIdAndTenantId(planId, tenantId)
            .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));
        plan.setIsActive(false);
        planRepository.save(plan);
        log.info("Plan deactivated: {}", planId);
    }
}
