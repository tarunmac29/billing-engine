package com.paycycle.billing_engine.application.service;

import com.paycycle.billing_engine.api.dto.request.CreatePlanRequest;
import com.paycycle.billing_engine.api.dto.response.PlanResponse;
import com.paycycle.billing_engine.api.exception.ResourceNotFoundException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public PlanResponse createPlan(String tenantId, CreatePlanRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        Plan plan = Plan.builder()
            .tenant(tenant).name(request.getName())
            .description(request.getDescription()).price(request.getPrice())
            .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
            .billingInterval(request.getBillingInterval())
            .trialDays(request.getTrialDays() != null ? request.getTrialDays() : 0)
            .gracePeriodDays(request.getGracePeriodDays() != null ? request.getGracePeriodDays() : 3)
            .maxRetryCount(request.getMaxRetryCount() != null ? request.getMaxRetryCount() : 3)
            .isActive(true).build();
        Plan saved = planRepository.save(plan);
        log.info("Plan created: {}", saved.getId());
        return PlanResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans(String tenantId) {
        return planRepository.findByTenantIdAndIsActive(tenantId, true)
            .stream().map(PlanResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(String tenantId, String planId) {
        return PlanResponse.from(planRepository.findByIdAndTenantId(planId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId)));
    }

    @Transactional
    public void deactivatePlan(String tenantId, String planId) {
        Plan plan = planRepository.findByIdAndTenantId(planId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        plan.setIsActive(false);
        planRepository.save(plan);
    }
}
