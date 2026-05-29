package com.paycycle.billing_engine.api.controller;

import com.paycycle.billing_engine.api.dto.request.CreatePlanRequest;
import com.paycycle.billing_engine.api.dto.response.PlanResponse;
import com.paycycle.billing_engine.application.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PlanController — REST API endpoints for Plans.
 *
 * Base URL: /api/v1/tenants/{tenantId}/plans
 *
 * Endpoints:
 *   POST   /api/v1/tenants/{tenantId}/plans          → Plan create karo
 *   GET    /api/v1/tenants/{tenantId}/plans          → Saare active plans dekho
 *   GET    /api/v1/tenants/{tenantId}/plans/{planId} → Ek plan dekho
 *   DELETE /api/v1/tenants/{tenantId}/plans/{planId} → Plan deactivate karo
 *
 * @RestController = @Controller + @ResponseBody
 * Matlab: return kiya hua object automatically JSON ban jaata hai
 */
@Slf4j
@RestController
@RequestMapping("/v1/tenants/{tenantId}/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    // POST /v1/tenants/{tenantId}/plans
    // Body: CreatePlanRequest JSON
    // @Valid — request body validate karega (@NotBlank, @DecimalMin etc.)
    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(
            @PathVariable String tenantId,
            @Valid @RequestBody CreatePlanRequest request) {

        log.info("POST /v1/tenants/{}/plans - Creating plan: {}", tenantId, request.getName());
        PlanResponse response = planService.createPlan(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response); // 201 Created
    }

    // GET /v1/tenants/{tenantId}/plans
    @GetMapping
    public ResponseEntity<List<PlanResponse>> getPlans(@PathVariable String tenantId) {
        return ResponseEntity.ok(planService.getActivePlans(tenantId));
    }

    // GET /v1/tenants/{tenantId}/plans/{planId}
    @GetMapping("/{planId}")
    public ResponseEntity<PlanResponse> getPlan(
            @PathVariable String tenantId,
            @PathVariable String planId) {
        return ResponseEntity.ok(planService.getPlan(tenantId, planId));
    }

    // DELETE /v1/tenants/{tenantId}/plans/{planId}
    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> deactivatePlan(
            @PathVariable String tenantId,
            @PathVariable String planId) {
        planService.deactivatePlan(tenantId, planId);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
