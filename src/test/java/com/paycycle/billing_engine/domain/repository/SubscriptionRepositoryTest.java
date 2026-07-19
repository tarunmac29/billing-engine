package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.*;
import com.paycycle.billing_engine.domain.enums.BillingInterval;
import com.paycycle.billing_engine.domain.enums.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubscriptionRepositoryTest — Real MySQL se test karo.
 *
 * ============================================================
 * 
 * @Testcontainers — Docker container manage karta hai
 * @Container — MySQL container start/stop automatically
 * @DataJpaTest — sirf JPA layer load hoti hai (fast!)
 *              ============================================================
 *
 *              Ye test REAL MySQL Docker container use karta hai!
 *              Matlab: actual queries, actual indexes, actual constraints.
 *              In-memory H2 nahi — production jaise environment.
 *
 *              Iske liye Docker Desktop chalna chahiye.
 */

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("SubscriptionRepository Tests")
class SubscriptionRepositoryTest {

    // MySQL container — test ke liye automatically start hoga
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("paycycle_test")
            .withUsername("test")
            .withPassword("test");

    // Spring ko container ka URL batao
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "false"); // test mein Flyway off
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // 👇 LINE A: Hibernate ko bolo ki tables fresh banaye
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // 👇 LINE B (THE FIX): Missing constraints drop karte waqt failure block hatane
        // ke liye
        registry.add("spring.jpa.properties.hibernate.id.new_generator_mappings", () -> "true");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
    }

    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PlanRepository planRepository;

    private Tenant savedTenant;
    private Customer savedCustomer;
    private Plan savedPlan;

    void setupData() {

        String uniqueDomain = "test-" + System.currentTimeMillis() + ".paycycle.com";
        String uniqueHash = "hash-" + System.currentTimeMillis();

        Tenant tenant = new Tenant();
        tenant.setName("Test Tenant");
        tenant.setDomain(uniqueDomain);
        tenant.setApiKeyHash(uniqueHash);
        tenant.setIsActive(true);
        savedTenant = tenantRepository.save(tenant);

        Customer customer = new Customer();
        customer.setTenant(savedTenant);
        customer.setEmail("test@test.com");
        customer.setName("Test User");
        customer.setIsActive(true);
        savedCustomer = customerRepository.save(customer);

        Plan plan = new Plan();
        plan.setTenant(savedTenant);
        plan.setName("Test Plan");
        plan.setPrice(new BigDecimal("99.00"));
        plan.setCurrency("INR");
        plan.setBillingInterval(BillingInterval.MONTHLY);
        plan.setTrialDays(0);
        plan.setGracePeriodDays(3);
        plan.setMaxRetryCount(3);
        plan.setIsActive(true);
        savedPlan = planRepository.save(plan);
    }

    @Test
    @DisplayName("Due subscriptions billing harvester query se milni chahiye")
    void shouldFindDueSubscriptions_withBillingHarvesterQuery() {
        setupData();

        // Arrange — due subscription banao
        Subscription dueSub = new Subscription();
        dueSub.setTenant(savedTenant);
        dueSub.setCustomer(savedCustomer);
        dueSub.setPlan(savedPlan);
        dueSub.setStatus(SubscriptionStatus.ACTIVE);
        dueSub.setCurrentPeriodStart(LocalDateTime.now().minusMonths(1));
        dueSub.setCurrentPeriodEnd(LocalDateTime.now().minusHours(1)); // EXPIRED!
        dueSub.setRetryCount(0);
        subscriptionRepository.save(dueSub);

        // Future subscription — due nahi hai
        Subscription futureSub = new Subscription();
        futureSub.setTenant(savedTenant);
        futureSub.setCustomer(savedCustomer);
        futureSub.setPlan(savedPlan);
        futureSub.setStatus(SubscriptionStatus.ACTIVE);
        futureSub.setCurrentPeriodStart(LocalDateTime.now());
        futureSub.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1)); // FUTURE
        futureSub.setRetryCount(0);
        subscriptionRepository.save(futureSub);

        // Act — billing harvester query
        Page<Subscription> result = subscriptionRepository.findDueSubscriptions(
                savedTenant.getId(),
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING),
                LocalDateTime.now(),
                PageRequest.of(0, 10));

        // Assert — sirf due subscription milni chahiye
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus())
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getContent().get(0).getCurrentPeriodEnd())
                .isBefore(LocalDateTime.now());
    }

    @Test
    @DisplayName("CANCELLED subscription billing query mein nahi aani chahiye")
    void shouldNotFindCancelledSubscriptions_inBillingQuery() {
        setupData();

        // Cancelled subscription
        Subscription cancelledSub = new Subscription();
        cancelledSub.setTenant(savedTenant);
        cancelledSub.setCustomer(savedCustomer);
        cancelledSub.setPlan(savedPlan);
        cancelledSub.setStatus(SubscriptionStatus.CANCELLED); // CANCELLED
        cancelledSub.setCurrentPeriodStart(LocalDateTime.now().minusMonths(1));
        cancelledSub.setCurrentPeriodEnd(LocalDateTime.now().minusHours(1));
        cancelledSub.setRetryCount(0);
        subscriptionRepository.save(cancelledSub);

        // Act
        Page<Subscription> result = subscriptionRepository.findDueSubscriptions(
                savedTenant.getId(),
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING),
                LocalDateTime.now(),
                PageRequest.of(0, 10));

        // Assert — cancelled subscription nahi aani chahiye
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Tenant isolation — dusre tenant ki subscriptions nahi milni chahiye")
    void shouldIsolateTenantData() {
        setupData();

        // Dusra tenant banao
        Tenant otherTenant = new Tenant();
        otherTenant.setName("Other Tenant");
        otherTenant.setDomain("other.paycycle.com");
        otherTenant.setApiKeyHash("other-hash-456");
        otherTenant.setIsActive(true);
        Tenant savedOtherTenant = tenantRepository.save(otherTenant);

        // Other tenant ka customer
        Customer otherCustomer = new Customer();
        otherCustomer.setTenant(savedOtherTenant);
        otherCustomer.setEmail("other@test.com");
        otherCustomer.setName("Other User");
        otherCustomer.setIsActive(true);
        Customer savedOtherCustomer = customerRepository.save(otherCustomer);

        // Other tenant ka plan
        Plan otherPlan = new Plan();
        otherPlan.setTenant(savedOtherTenant);
        otherPlan.setName("Other Plan");
        otherPlan.setPrice(new BigDecimal("199.00"));
        otherPlan.setCurrency("INR");
        otherPlan.setBillingInterval(BillingInterval.MONTHLY);
        otherPlan.setTrialDays(0);
        otherPlan.setGracePeriodDays(3);
        otherPlan.setMaxRetryCount(3);
        otherPlan.setIsActive(true);
        Plan savedOtherPlan = planRepository.save(otherPlan);

        // Other tenant ki due subscription
        Subscription otherSub = new Subscription();
        otherSub.setTenant(savedOtherTenant);
        otherSub.setCustomer(savedOtherCustomer);
        otherSub.setPlan(savedOtherPlan);
        otherSub.setStatus(SubscriptionStatus.ACTIVE);
        otherSub.setCurrentPeriodStart(LocalDateTime.now().minusMonths(1));
        otherSub.setCurrentPeriodEnd(LocalDateTime.now().minusHours(1));
        otherSub.setRetryCount(0);
        subscriptionRepository.save(otherSub);

        // Act — pehle tenant ki query
        Page<Subscription> result = subscriptionRepository.findDueSubscriptions(
                savedTenant.getId(), // sirf pehle tenant ki
                List.of(SubscriptionStatus.ACTIVE),
                LocalDateTime.now(),
                PageRequest.of(0, 10));

        // Assert — dusre tenant ki subscription nahi milni chahiye
        assertThat(result.getContent()).isEmpty();
    }
}
