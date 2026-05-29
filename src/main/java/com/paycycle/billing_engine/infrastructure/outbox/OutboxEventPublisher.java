package com.paycycle.billing_engine.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycycle.billing_engine.domain.entity.OutboxEvent;
import com.paycycle.billing_engine.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OutboxEventPublisher
 *
 * Business logic ke saath SAME @Transactional mein call karo.
 * Ye Kafka ko seedha nahi bhejta — outbox table mein likhta hai.
 * OutboxRelayScheduler baad mein Kafka par bhejega.
 *
 * Usage:
 *   outboxPublisher.publish("INVOICE", invoice.getId(),
 *       "invoice.paid", "billing.events", Map.of("amount", 99));
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publish(
            String aggregateType,
            String aggregateId,
            String eventType,
            String kafkaTopic,
            Object payload) {

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .kafkaTopic(kafkaTopic)
                .payload(payloadJson)
                .published(false)
                .retryCount(0)
                .build();

            outboxEventRepository.save(event);
            log.debug("Outbox event saved: {} for {}: {}", eventType, aggregateType, aggregateId);

        } catch (Exception e) {
            log.error("Failed to save outbox event: {}", e.getMessage());
            throw new RuntimeException("Outbox event save failed", e);
        }
    }
}
