package com.paycycle.billing_engine.application.scheduler;

import com.paycycle.billing_engine.application.service.BillingCycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BillingTestController — Development only!
 *
 * Billing harvester ko manually trigger karne ke liye.
 * Normally ye @Scheduled se har ghante chalta hai.
 * Testing ke liye manually trigger karte hain.
 *
 * POST /v1/internal/billing/trigger
 */
@RestController
@RequestMapping("/v1/internal")
@RequiredArgsConstructor
public class BillingTestController {

    private final BillingCycleService billingCycleService;

    @PostMapping("/billing/trigger")
    public ResponseEntity<String> triggerBilling() {
        billingCycleService.harvestDueSubscriptions();
        return ResponseEntity.ok("Billing cycle triggered! Check logs.");
    }
}
