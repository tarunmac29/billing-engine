package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    // Relay scheduler ke liye — unpublished events batch mein lo
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc(Pageable pageable);
}
