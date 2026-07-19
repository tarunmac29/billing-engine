package com.paycycle.billing_engine.application.service;

import com.paycycle.billing_engine.api.dto.request.CreateSubscriptionRequest;
import com.paycycle.billing_engine.api.dto.response.SubscriptionResponse;
import com.paycycle.billing_engine.domain.entity.*;
import com.paycycle.billing_engine.domain.enums.BillingInterval;
import com.paycycle.billing_engine.domain.enums.SubscriptionStatus;
import com.paycycle.billing_engine.domain.repository.CustomerRepository;
import com.paycycle.billing_engine.domain.repository.PlanRepository;
import com.paycycle.billing_engine.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SubscriptionServiceTest — State Machine ka poora test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService Tests")
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private PlanRepository planRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Tenant tenant;
    private Customer customer;
    private Plan planWithTrial;
    private Plan planWithoutTrial;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId("tenant-123");
        tenant.setName("Test Tenant");

        customer = new Customer();
        customer.setId("customer-123");
        customer.setEmail("rahul@test.com");
        customer.setName("Rahul Sharma");
        customer.setTenant(tenant);
        customer.setIsActive(true);

        planWithTrial = new Plan();
        planWithTrial.setId("plan-trial-123");
        planWithTrial.setName("Basic Monthly");
        planWithTrial.setPrice(new BigDecimal("99.00"));
        planWithTrial.setCurrency("INR");
        planWithTrial.setBillingInterval(BillingInterval.MONTHLY);
        planWithTrial.setTrialDays(7);
        planWithTrial.setGracePeriodDays(3);
        planWithTrial.setMaxRetryCount(3);
        planWithTrial.setIsActive(true);
        planWithTrial.setTenant(tenant);

        planWithoutTrial = new Plan();
        planWithoutTrial.setId("plan-notrial-123");
        planWithoutTrial.setName("Pro Monthly");
        planWithoutTrial.setPrice(new BigDecimal("299.00"));
        planWithoutTrial.setCurrency("INR");
        planWithoutTrial.setBillingInterval(BillingInterval.MONTHLY);
        planWithoutTrial.setTrialDays(0);
        planWithoutTrial.setGracePeriodDays(3);
        planWithoutTrial.setMaxRetryCount(3);
        planWithoutTrial.setIsActive(true);
        planWithoutTrial.setTenant(tenant);

        subscription = new Subscription();
        subscription.setId("sub-123");
        subscription.setTenant(tenant);
        subscription.setCustomer(customer);
        subscription.setPlan(planWithTrial);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(LocalDateTime.now().minusDays(1));
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
        subscription.setRetryCount(0);
        subscription.setVersion(0L);
    }

    // ============================================================
    // CREATE SUBSCRIPTION TESTS
    // ============================================================
    @Nested
    @DisplayName("createSubscription()")
    class CreateSubscriptionTests {

        @Test
        @DisplayName("Trial wale plan mein TRIALING status hona chahiye")
        void shouldCreateSubscriptionWithTrialingStatus_whenPlanHasTrial() {
            CreateSubscriptionRequest request = new CreateSubscriptionRequest();
            request.setCustomerId("customer-123");
            request.setPlanId("plan-trial-123");

            when(customerRepository.findById("customer-123"))
                .thenReturn(Optional.of(customer));
            when(planRepository.findByIdAndTenantId("plan-trial-123", "tenant-123"))
                .thenReturn(Optional.of(planWithTrial));
            when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService
                .createSubscription("tenant-123", request);

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
            assertThat(response.getTrialEnd()).isNotNull();
            assertThat(response.getTrialEnd())
                .isAfter(LocalDateTime.now().plusDays(6));
            verify(subscriptionRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("Trial nahi hai toh seedha ACTIVE hona chahiye")
        void shouldCreateSubscriptionWithActiveStatus_whenPlanHasNoTrial() {
            CreateSubscriptionRequest request = new CreateSubscriptionRequest();
            request.setCustomerId("customer-123");
            request.setPlanId("plan-notrial-123");

            when(customerRepository.findById("customer-123"))
                .thenReturn(Optional.of(customer));
            when(planRepository.findByIdAndTenantId("plan-notrial-123", "tenant-123"))
                .thenReturn(Optional.of(planWithoutTrial));
            when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService
                .createSubscription("tenant-123", request);

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(response.getTrialEnd()).isNull();
        }

        @Test
        @DisplayName("Customer nahi mila toh RuntimeException aani chahiye")
        void shouldThrowException_whenCustomerNotFound() {
            // Arrange
            CreateSubscriptionRequest request = new CreateSubscriptionRequest();
            request.setCustomerId("wrong-id");
            request.setPlanId("plan-123");

            when(customerRepository.findById("wrong-id"))
                .thenReturn(Optional.empty());

            // Act & Assert — Expect RuntimeException to match real engine implementation code
            assertThatThrownBy(() ->
                subscriptionService.createSubscription("tenant-123", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Customer not found: wrong-id");
        }

        @Test
        @DisplayName("Inactive plan pe subscribe nahi ho sakta")
        void shouldThrowException_whenPlanIsInactive() {
            planWithTrial.setIsActive(false);
            CreateSubscriptionRequest request = new CreateSubscriptionRequest();
            request.setCustomerId("customer-123");
            request.setPlanId("plan-trial-123");

            when(customerRepository.findById("customer-123"))
                .thenReturn(Optional.of(customer));
            when(planRepository.findByIdAndTenantId("plan-trial-123", "tenant-123"))
                .thenReturn(Optional.of(planWithTrial));

            assertThatThrownBy(() ->
                subscriptionService.createSubscription("tenant-123", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not active");
        }
    }

    // ============================================================
    // STATE MACHINE TESTS
    // ============================================================
    @Nested
    @DisplayName("State Machine Transitions")
    class StateMachineTests {

        @Test
        @DisplayName("TRIALING → ACTIVE transition sahi kaam karna chahiye")
        void shouldActivateSubscription_fromTrialing() {
            subscription.setStatus(SubscriptionStatus.TRIALING);
            subscription.setTrialEnd(LocalDateTime.now().minusDays(1));

            when(subscriptionRepository.findByIdAndTenantId("sub-123", "tenant-123"))
                .thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService
                .activateSubscription("tenant-123", "sub-123");

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(response.getCurrentPeriodEnd()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("ACTIVE → PAUSED transition sahi kaam karna chahiye")
        void shouldPauseSubscription_fromActive() {
            when(subscriptionRepository.findByIdAndTenantId("sub-123", "tenant-123"))
                .thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService
                .pauseSubscription("tenant-123", "sub-123");

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        }

        @Test
        @DisplayName("PAUSED → ACTIVE (resume) transition sahi kaam karna chahiye")
        void shouldResumeSubscription_fromPaused() {
            subscription.setStatus(SubscriptionStatus.PAUSED);

            when(subscriptionRepository.findByIdAndTenantId("sub-123", "tenant-123"))
                .thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService
                .resumeSubscription("tenant-123", "sub-123");

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("CANCELLED subscription ko activate nahi kar sakte")
        void shouldThrowException_whenActivatingCancelledSubscription() {
            subscription.setStatus(SubscriptionStatus.CANCELLED);

            when(subscriptionRepository.findByIdAndTenantId("sub-123", "tenant-123"))
                .thenReturn(Optional.of(subscription));

            assertThatThrownBy(() ->
                subscriptionService.activateSubscription("tenant-123", "sub-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid transition");
        }

        @Test
        @DisplayName("ACTIVE → CANCELLED immediately = true")
        void shouldCancelSubscription_immediately() {
            when(subscriptionRepository.findByIdAndTenantId("sub-123", "tenant-123"))
                .thenReturn(Optional.of(subscription));
            when(subscriptionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

            SubscriptionResponse response = subscriptionService
                .cancelSubscription("tenant-123", "sub-123", true);

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        }
    }

    // ============================================================
    // BILLING INTERVAL TESTS
    // ============================================================
    @Nested
    @DisplayName("calculatePeriodEnd()")
    class PeriodCalculationTests {

        @Test
        @DisplayName("MONTHLY interval → 1 month baad")
        void shouldCalculateMonthlyPeriod() {
            LocalDateTime from = LocalDateTime.of(2026, 1, 15, 10, 0);
            LocalDateTime expected = LocalDateTime.of(2026, 2, 15, 10, 0);

            LocalDateTime result = subscriptionService
                .calculatePeriodEnd(from, BillingInterval.MONTHLY);

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("YEARLY interval → 1 saal baad")
        void shouldCalculateYearlyPeriod() {
            LocalDateTime from = LocalDateTime.of(2026, 1, 15, 10, 0);
            LocalDateTime result = subscriptionService
                .calculatePeriodEnd(from, BillingInterval.YEARLY);

            assertThat(result.getYear()).isEqualTo(2027);
        }

        @Test
        @DisplayName("WEEKLY interval → 7 din baad")
        void shouldCalculateWeeklyPeriod() {
            LocalDateTime from = LocalDateTime.now();
            LocalDateTime result = subscriptionService
                .calculatePeriodEnd(from, BillingInterval.WEEKLY);

            assertThat(result).isEqualTo(from.plusWeeks(1));
        }
    }
}