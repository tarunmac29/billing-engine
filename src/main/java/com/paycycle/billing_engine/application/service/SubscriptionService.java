package com.paycycle.billing_engine.application.service;

import com.paycycle.billing_engine.api.dto.request.CreateSubscriptionRequest;
import com.paycycle.billing_engine.api.dto.response.SubscriptionResponse;
import com.paycycle.billing_engine.domain.entity.Customer;
import com.paycycle.billing_engine.domain.entity.Plan;
import com.paycycle.billing_engine.domain.entity.Subscription;
import com.paycycle.billing_engine.domain.enums.BillingInterval;
import com.paycycle.billing_engine.domain.enums.SubscriptionStatus;
import com.paycycle.billing_engine.domain.repository.CustomerRepository;
import com.paycycle.billing_engine.domain.repository.PlanRepository;
import com.paycycle.billing_engine.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SubscriptionService — Core business logic.
 *
 * ============================================================
 * SUBSCRIPTION LIFECYCLE:
 * ============================================================
 *
 * 1. Customer subscribes karta hai
 *    → Agar plan mein trial_days > 0: status = TRIALING
 *    → Agar trial_days = 0: status = ACTIVE (immediately)
 *
 * 2. Trial khatam hota hai (@Scheduled se)
 *    → TRIALING → ACTIVE (payment successful)
 *    → TRIALING → EXPIRED (payment failed)
 *
 * 3. Billing period khatam hota hai
 *    → Payment attempt hota hai
 *    → Success: ACTIVE rehta hai, new period start
 *    → Fail: ACTIVE → PAST_DUE, retry schedule hota hai
 *
 * 4. Retry
 *    → Success: PAST_DUE → ACTIVE
 *    → Max retries exhausted: PAST_DUE → CANCELLED
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;

    @Transactional
    public SubscriptionResponse createSubscription(
            String tenantId,
            CreateSubscriptionRequest request) {

        log.info("Creating subscription for customer: {} plan: {}",
            request.getCustomerId(), request.getPlanId());

        // Customer exist karta hai is tenant mein?
        Customer customer = customerRepository.findById(request.getCustomerId())
            .filter(c -> c.getTenant().getId().equals(tenantId))
            .orElseThrow(() -> new RuntimeException(
                "Customer not found: " + request.getCustomerId()));

        // Plan exist karta hai is tenant mein?
        Plan plan = planRepository.findByIdAndTenantId(request.getPlanId(), tenantId)
            .orElseThrow(() -> new RuntimeException(
                "Plan not found: " + request.getPlanId()));

        // Plan active hai?
        if (!plan.getIsActive()) {
            throw new RuntimeException("Plan is not active: " + plan.getName());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = calculatePeriodEnd(now, plan.getBillingInterval());

        // Trial hai ki nahi?
        boolean hasTrial = plan.getTrialDays() > 0;
        LocalDateTime trialEnd = hasTrial
            ? now.plusDays(plan.getTrialDays())
            : null;

        Subscription subscription = Subscription.builder()
            .tenant(customer.getTenant())
            .customer(customer)
            .plan(plan)
            // Trial hai toh TRIALING, nahi toh seedha ACTIVE
            .status(hasTrial ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE)
            .currentPeriodStart(now)
            // Trial mein period_end = trial_end
            .currentPeriodEnd(hasTrial ? trialEnd : periodEnd)
            .trialEnd(trialEnd)
            .retryCount(0)
            .build();

        Subscription saved = subscriptionRepository.save(subscription);

        log.info("Subscription created: {} status: {}", saved.getId(), saved.getStatus());
        return SubscriptionResponse.from(saved);
    }

    // ============================================================
    // STATE TRANSITIONS
    // ============================================================

    /** TRIALING → ACTIVE (trial khatam, payment success) */
    @Transactional
    public SubscriptionResponse activateSubscription(String tenantId, String subscriptionId) {
        Subscription sub = findSubscription(tenantId, subscriptionId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newPeriodEnd = calculatePeriodEnd(now, sub.getPlan().getBillingInterval());

        // Domain method call karo — entity ke andar logic hai
        sub.activate(now, newPeriodEnd);
        Subscription saved = subscriptionRepository.save(sub);

        log.info("Subscription activated: {}", subscriptionId);
        return SubscriptionResponse.from(saved);
    }

    /** ACTIVE → CANCELLED */
    @Transactional
    public SubscriptionResponse cancelSubscription(
            String tenantId,
            String subscriptionId,
            boolean immediately) {

        Subscription sub = findSubscription(tenantId, subscriptionId);
        sub.cancel(immediately);
        Subscription saved = subscriptionRepository.save(sub);

        log.info("Subscription cancelled: {} immediately: {}", subscriptionId, immediately);
        return SubscriptionResponse.from(saved);
    }

    /** ACTIVE → PAUSED */
    @Transactional
    public SubscriptionResponse pauseSubscription(String tenantId, String subscriptionId) {
        Subscription sub = findSubscription(tenantId, subscriptionId);
        sub.pause();
        Subscription saved = subscriptionRepository.save(sub);
        log.info("Subscription paused: {}", subscriptionId);
        return SubscriptionResponse.from(saved);
    }

    /** PAUSED → ACTIVE */
    @Transactional
    public SubscriptionResponse resumeSubscription(String tenantId, String subscriptionId) {
        Subscription sub = findSubscription(tenantId, subscriptionId);
        LocalDateTime newPeriodEnd = calculatePeriodEnd(
            LocalDateTime.now(), sub.getPlan().getBillingInterval());
        sub.resume(newPeriodEnd);
        Subscription saved = subscriptionRepository.save(sub);
        log.info("Subscription resumed: {}", subscriptionId);
        return SubscriptionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getCustomerSubscriptions(
            String tenantId, String customerId) {
        return subscriptionRepository
            .findByCustomerIdAndStatus(customerId, SubscriptionStatus.ACTIVE)
            .stream()
            .map(SubscriptionResponse::from)
            .toList();
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private Subscription findSubscription(String tenantId, String subscriptionId) {
        return subscriptionRepository.findByIdAndTenantId(subscriptionId, tenantId)
            .orElseThrow(() -> new RuntimeException(
                "Subscription not found: " + subscriptionId));
    }

    /**
     * Billing interval ke hisaab se next period calculate karo.
     * MONTHLY → +1 month
     * YEARLY  → +1 year
     * etc.
     */
    public LocalDateTime calculatePeriodEnd(LocalDateTime from, BillingInterval interval) {
        return switch (interval) {
            case DAILY     -> from.plusDays(1);
            case WEEKLY    -> from.plusWeeks(1);
            case MONTHLY   -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case YEARLY    -> from.plusYears(1);
        };
    }
}
