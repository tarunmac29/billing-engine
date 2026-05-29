package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.Subscription;
import com.paycycle.billing_engine.domain.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    // Customer ki saari subscriptions
    List<Subscription> findByCustomerIdAndStatus(String customerId, SubscriptionStatus status);

    // Tenant ke andar specific subscription
    Optional<Subscription> findByIdAndTenantId(String id, String tenantId);

    // ============================================================
    // BILLING HARVESTER QUERY — Ye BillingCycleService use karega
    // ============================================================
    // "Saari wo subscriptions do jinki period_end abhi se pehle
    //  ho gayi aur status ACTIVE ya TRIALING hai"
    // Ye query har ghante @Scheduled se chalegi
    // idx_sub_billing_harvest index is query ko fast banata hai
    // ============================================================
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.tenant.id = :tenantId
          AND s.status IN :statuses
          AND s.currentPeriodEnd <= :now
        ORDER BY s.currentPeriodEnd ASC
        """)
    Page<Subscription> findDueSubscriptions(
        @Param("tenantId") String tenantId,
        @Param("statuses") List<SubscriptionStatus> statuses,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );

    // Retry queue — PAST_DUE aur retry time aa gayi
    @Query("""
        SELECT s FROM Subscription s
        WHERE s.status = 'PAST_DUE'
          AND s.nextRetryAt <= :now
          AND s.retryCount < s.plan.maxRetryCount
        ORDER BY s.nextRetryAt ASC
        """)
    List<Subscription> findSubscriptionsReadyForRetry(@Param("now") LocalDateTime now);
}
