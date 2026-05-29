package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.Invoice;
import com.paycycle.billing_engine.domain.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    List<Invoice> findBySubscriptionIdAndStatus(String subscriptionId, InvoiceStatus status);

    Optional<Invoice> findTopByTenantIdOrderByCreatedAtDesc(String tenantId);
}
