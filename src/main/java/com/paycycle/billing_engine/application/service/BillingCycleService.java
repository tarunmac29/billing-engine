package com.paycycle.billing_engine.application.service;

import com.paycycle.billing_engine.domain.entity.Invoice;
import com.paycycle.billing_engine.domain.entity.Subscription;
import com.paycycle.billing_engine.domain.enums.SubscriptionStatus;
import com.paycycle.billing_engine.domain.repository.InvoiceRepository;
import com.paycycle.billing_engine.domain.repository.SubscriptionRepository;
import com.paycycle.billing_engine.infrastructure.outbox.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BillingCycleService — Ye poore project ka HEART hai.
 *
 * ============================================================
 * Kya karta hai:
 * ============================================================
 * Har ghante @Scheduled se chalega aur dhundega:
 * "Kaun se subscriptions ka period khatam hua?"
 *
 * Phir har subscription ko @Async se alag thread mein
 * charge karega. 10 lakh subscriptions bhi crash nahi
 * karenge kyunki:
 *   - Batch mein kaam hoga (500 ek baar)
 *   - Har subscription alag thread mein
 *   - Ek fail hone se doosre affect nahi hote
 *
 * ============================================================
 * Concurrency Safety:
 * ============================================================
 * Subscription par @Version hai (Optimistic Locking)
 * Agar 2 threads ek saath same subscription charge karein:
 *   - Ek succeed karega
 *   - Doosra OptimisticLockException paayega
 *   - Next cycle mein retry hoga
 * Double charge IMPOSSIBLE hai
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCycleService {

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionService subscriptionService;
    private final OutboxEventPublisher outboxEventPublisher;

    private static final int BATCH_SIZE = 500;
    private static final int MAX_PAGES = 20; // safety limit

    // ============================================================
    // MAIN HARVESTER — Har ghante chalega
    // "0 0 * * * *" = minute=0, second=0 (top of every hour)
    // ============================================================
    @Scheduled(cron = "0 0 * * * *")
    public void harvestDueSubscriptions() {
        log.info("=== BILLING HARVEST STARTED: {} ===", LocalDateTime.now());

        AtomicInteger totalProcessed = new AtomicInteger(0);
        LocalDateTime now = LocalDateTime.now();

        List<SubscriptionStatus> billableStatuses = List.of(
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.TRIALING
        );

        // Batch mein process karo — ek baar mein BATCH_SIZE subscriptions
        // Jab tak kaam baaki hai chalte raho (lekin MAX_PAGES tak)
        for (int page = 0; page < MAX_PAGES; page++) {
            Page<Subscription> batch = subscriptionRepository.findDueSubscriptions(
                // Note: production mein tenant-aware hoga
                "a0000000-0000-0000-0000-000000000001",
                billableStatuses,
                now,
                PageRequest.of(page, BATCH_SIZE)
            );

            if (batch.isEmpty()) break;

            log.info("Processing batch {}, size: {}", page + 1, batch.getContent().size());

            // Har subscription ASYNC mein process karo
            batch.getContent().forEach(this::processSubscriptionAsync);
            totalProcessed.addAndGet(batch.getContent().size());

            if (!batch.hasNext()) break;
        }

        log.info("=== BILLING HARVEST COMPLETE. Total: {} ===", totalProcessed.get());
    }

    // ============================================================
    // ASYNC PROCESSOR — Har subscription alag thread mein
    // @Async — ye method caller thread mein NAHI chalega
    // ThreadPoolTaskExecutor mein submit hoga
    // ============================================================
    @Async("billingExecutor")
    public void processSubscriptionAsync(Subscription subscription) {
        try {
            processSubscription(subscription);
        } catch (Exception e) {
            log.error("Failed to process subscription {}: {}",
                subscription.getId(), e.getMessage());
        }
    }

    // ============================================================
    // CORE BILLING LOGIC — Ek subscription charge karo
    // ============================================================
    @Transactional
    public void processSubscription(Subscription subscription) {
        log.info("Processing subscription: {} status: {}",
            subscription.getId(), subscription.getStatus());

        // Trial check
        if (subscription.getStatus() == SubscriptionStatus.TRIALING) {
            if (subscription.isTrialExpired()) {
                // Trial khatam — activate karo
                processTrialEnd(subscription);
            }
            return;
        }

        // Billing period khatam hua?
        if (!subscription.isPeriodExpired()) {
            return; // abhi charge nahi karna
        }

        // Invoice banao
        Invoice invoice = createInvoice(subscription);

        // Payment simulate karo (Phase 4 mein real gateway lagega)
        boolean paymentSuccess = simulatePayment(subscription, invoice);

        if (paymentSuccess) {
            // Payment success — next period shuru karo
            handlePaymentSuccess(subscription, invoice);
        } else {
            // Payment fail — retry schedule karo
            handlePaymentFailure(subscription, invoice);
        }
    }

    private void processTrialEnd(Subscription subscription) {
        log.info("Trial expired for subscription: {}", subscription.getId());

        // Dev tenant ID hardcoded — production mein dynamic hoga
        String tenantId = subscription.getTenant().getId();
        subscriptionService.activateSubscription(tenantId, subscription.getId());

        // Outbox event — same transaction mein
        outboxEventPublisher.publish(
            "SUBSCRIPTION",
            subscription.getId(),
            "subscription.trial_ended",
            "billing.events",
            Map.of(
                "subscriptionId", subscription.getId(),
                "customerId", subscription.getCustomer().getId(),
                "planId", subscription.getPlan().getId()
            )
        );
    }

    private Invoice createInvoice(Subscription subscription) {
        String invoiceNumber = "INV-" + System.currentTimeMillis();

        Invoice invoice = Invoice.builder()
            .tenant(subscription.getTenant())
            .subscription(subscription)
            .invoiceNumber(invoiceNumber)
            .amountDue(subscription.getPlan().getPrice())
            .currency(subscription.getPlan().getCurrency())
            .dueDate(LocalDate.now())
            .periodStart(subscription.getCurrentPeriodStart())
            .periodEnd(subscription.getCurrentPeriodEnd())
            .description("Billing for " + subscription.getPlan().getName())
            .build();

        invoice.open(); // DRAFT → OPEN
        return invoiceRepository.save(invoice);
    }

    // private boolean simulatePayment(Subscription subscription, Invoice invoice) {
    //     // TODO Phase 4: Real Stripe/Razorpay gateway call
    //     // Abhi 90% success simulate kar rahe hain
    //     boolean success = Math.random() > 0.1;
    //     log.info("Payment simulation for {}: {}", invoice.getId(),
    //         success ? "SUCCESS" : "FAILED");
    //     return success;
    // }

    private boolean simulatePayment(Subscription subscription, Invoice invoice) {
    // TODO Phase 4: Real Stripe/Razorpay gateway call
    boolean success = simulatePaymentForTest();
    log.info("Payment simulation for {}: {}", invoice.getId(),
        success ? "SUCCESS" : "FAILED");
    return success;
}

    // Test ke liye — spy se override kar sakte hain
    public boolean simulatePaymentForTest() {
        return Math.random() > 0.1;
    }

    private void handlePaymentSuccess(Subscription subscription, Invoice invoice) {
        // Invoice paid mark karo
        invoice.markPaid();
        invoiceRepository.save(invoice);

        // Next billing period calculate karo
        LocalDateTime newPeriodStart = LocalDateTime.now();
        LocalDateTime newPeriodEnd = subscriptionService
            .calculatePeriodEnd(newPeriodStart, subscription.getPlan().getBillingInterval());

        subscription.activate(newPeriodStart, newPeriodEnd);
        subscriptionRepository.save(subscription);

        log.info("Payment SUCCESS for subscription: {}. Next billing: {}",
            subscription.getId(), newPeriodEnd);

        // Outbox event — SAME transaction mein
        outboxEventPublisher.publish(
            "INVOICE",
            invoice.getId(),
            "invoice.paid",
            "billing.events",
            Map.of(
                "invoiceId", invoice.getId(),
                "subscriptionId", subscription.getId(),
                "amount", invoice.getAmountDue(),
                "currency", invoice.getCurrency()
            )
        );
    }

    private void handlePaymentFailure(Subscription subscription, Invoice invoice) {
        int maxRetries = subscription.getPlan().getMaxRetryCount();
        int currentRetries = subscription.getRetryCount();

        if (currentRetries >= maxRetries) {
            // Max retries exhausted — cancel karo
            log.warn("Max retries exhausted for subscription: {}. Cancelling.",
                subscription.getId());

            invoice.markUncollectible();
            invoiceRepository.save(invoice);

            subscription.cancel(true);
            subscriptionRepository.save(subscription);

            outboxEventPublisher.publish(
                "SUBSCRIPTION",
                subscription.getId(),
                "subscription.cancelled.non_payment",
                "billing.events",
                Map.of("subscriptionId", subscription.getId(),
                       "reason", "MAX_RETRIES_EXHAUSTED")
            );
        } else {
            // Retry schedule karo — exponential backoff
            // Retry 1: 1 din baad, Retry 2: 2 din baad, Retry 3: 4 din baad
            int daysUntilRetry = (int) Math.pow(2, currentRetries);
            LocalDateTime nextRetry = LocalDateTime.now().plusDays(daysUntilRetry);

            subscription.markPastDue(nextRetry);
            subscriptionRepository.save(subscription);

            log.warn("Payment FAILED for subscription: {}. Retry #{} at: {}",
                subscription.getId(), currentRetries + 1, nextRetry);

            outboxEventPublisher.publish(
                "INVOICE",
                invoice.getId(),
                "invoice.payment_failed",
                "billing.events",
                Map.of("invoiceId", invoice.getId(),
                       "retryCount", currentRetries + 1,
                       "nextRetryAt", nextRetry.toString())
            );
        }
    }
}
