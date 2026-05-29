package com.paycycle.billing_engine.application.scheduler;

import com.paycycle.billing_engine.domain.entity.OutboxEvent;
import com.paycycle.billing_engine.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * OutboxRelayScheduler — Outbox events ko Kafka par bhejta hai.
 *
 * Har 5 second mein chalega.
 * Ek baar mein max 50 events process karega (batch).
 *
 * Abhi ke liye Kafka ki jagah simple log kar rahe hain.
 * Phase 4 mein actual Kafka integration karenge.
 *
 * @Scheduled(fixedDelay) — pichla execution khatam hone ke
 * baad 5 second wait karo (overlap prevent karta hai)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelay = 5000) // har 5 second mein
    @Transactional
    public void relayOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
            .findByPublishedFalseOrderByCreatedAtAsc(
                PageRequest.of(0, BATCH_SIZE)
            );

        if (pendingEvents.isEmpty()) {
            return; // kuch nahi hai toh quietly return karo
        }

        log.info("Relaying {} outbox events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // TODO Phase 4: kafkaTemplate.send(event.getKafkaTopic(), event.getPayload())
                // Abhi sirf log kar rahe hain
                log.info("EVENT PUBLISHED → topic: {} | type: {} | id: {}",
                    event.getKafkaTopic(),
                    event.getEventType(),
                    event.getAggregateId());

                event.markPublished();
                outboxEventRepository.save(event);

            } catch (Exception e) {
                log.error("Failed to relay event {}: {}", event.getId(), e.getMessage());
                event.markFailed(e.getMessage());
                outboxEventRepository.save(event);
            }
        }

        log.info("Outbox relay complete. Processed: {}", pendingEvents.size());
    }
}
