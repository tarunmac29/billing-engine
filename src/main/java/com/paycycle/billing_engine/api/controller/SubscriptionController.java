package com.paycycle.billing_engine.api.controller;

import com.paycycle.billing_engine.api.dto.request.CreateSubscriptionRequest;
import com.paycycle.billing_engine.api.dto.response.SubscriptionResponse;
import com.paycycle.billing_engine.application.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SubscriptionController
 *
 * POST   /v1/tenants/{tenantId}/subscriptions              → Subscribe karo
 * GET    /v1/tenants/{tenantId}/customers/{id}/subscriptions → Customer ki subscriptions
 * POST   /v1/tenants/{tenantId}/subscriptions/{id}/activate → TRIALING → ACTIVE
 * POST   /v1/tenants/{tenantId}/subscriptions/{id}/cancel   → ACTIVE → CANCELLED
 * POST   /v1/tenants/{tenantId}/subscriptions/{id}/pause    → ACTIVE → PAUSED
 * POST   /v1/tenants/{tenantId}/subscriptions/{id}/resume   → PAUSED → ACTIVE
 */
@RestController
@RequestMapping("/v1/tenants/{tenantId}")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(subscriptionService.createSubscription(tenantId, request));
    }

    @GetMapping("/customers/{customerId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> getCustomerSubscriptions(
            @PathVariable String tenantId,
            @PathVariable String customerId) {
        return ResponseEntity.ok(
            subscriptionService.getCustomerSubscriptions(tenantId, customerId));
    }

    // State transitions — POST use karo (action hai, resource update nahi)
    @PostMapping("/subscriptions/{subscriptionId}/activate")
    public ResponseEntity<SubscriptionResponse> activate(
            @PathVariable String tenantId,
            @PathVariable String subscriptionId) {
        return ResponseEntity.ok(
            subscriptionService.activateSubscription(tenantId, subscriptionId));
    }

    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    public ResponseEntity<SubscriptionResponse> cancel(
            @PathVariable String tenantId,
            @PathVariable String subscriptionId,
            @RequestParam(defaultValue = "false") boolean immediately) {
        return ResponseEntity.ok(
            subscriptionService.cancelSubscription(tenantId, subscriptionId, immediately));
    }

    @PostMapping("/subscriptions/{subscriptionId}/pause")
    public ResponseEntity<SubscriptionResponse> pause(
            @PathVariable String tenantId,
            @PathVariable String subscriptionId) {
        return ResponseEntity.ok(
            subscriptionService.pauseSubscription(tenantId, subscriptionId));
    }

    @PostMapping("/subscriptions/{subscriptionId}/resume")
    public ResponseEntity<SubscriptionResponse> resume(
            @PathVariable String tenantId,
            @PathVariable String subscriptionId) {
        return ResponseEntity.ok(
            subscriptionService.resumeSubscription(tenantId, subscriptionId));
    }
}
