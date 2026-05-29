package com.paycycle.billing_engine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AsyncConfig — Thread pool configuration.
 *
 * ============================================================
 * @EnableAsync   — @Async annotations kaam karein
 * @EnableScheduling — @Scheduled annotations kaam karein
 * ============================================================
 *
 * billingExecutor — BillingCycleService ke liye dedicated pool
 *   corePoolSize = 10   → hamesha 10 threads ready
 *   maxPoolSize  = 50   → load zyada ho toh 50 tak badho
 *   queueCapacity = 5000 → 5000 tasks queue mein wait kar sakte
 *
 * Analogy: Restaurant mein normally 10 waiters hain.
 * Rush hour mein 50 tak aa sakte hain.
 * 5000 customers queue mein wait kar sakte hain.
 * Usse zyada aaye toh "Sorry, full" (RejectedExecutionException)
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "billingExecutor")
    public Executor billingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("billing-async-");

        // Agar queue full ho jaaye toh caller thread mein run karo
        // (reject mat karo)
        executor.setRejectedExecutionHandler(
            (r, exec) -> {
                log.warn("Billing executor queue full! Running in caller thread.");
                r.run();
            }
        );

        executor.initialize();
        return executor;
    }
}
