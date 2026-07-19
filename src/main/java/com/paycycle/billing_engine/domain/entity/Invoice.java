package com.paycycle.billing_engine.domain.entity;

import com.paycycle.billing_engine.domain.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * INVOICE — Ek billing cycle ka charge record.
 *
 * Subscription → Invoice → Payment
 * Har period mein ek invoice banta hai.
 *
 * Status flow:
 *   DRAFT → OPEN → PAID
 *               → UNCOLLECTIBLE (max retries fail)
 *               → VOID (cancel)
 */
@Entity
@Table(name = "invoice")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "invoice_number", nullable = false, length = 30)
    private String invoiceNumber;

    // BigDecimal — KABHI float/double mat use karo money ke liye!
    @Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    // Domain methods
    public void markPaid() {
        this.status = InvoiceStatus.PAID;
        this.amountPaid = this.amountDue;
        this.paidAt = LocalDateTime.now();
    }

    public void markUncollectible() {
        this.status = InvoiceStatus.UNCOLLECTIBLE;
    }

    public void open() {
        this.status = InvoiceStatus.OPEN;
    }

    public boolean isPaid() {
        return this.status == InvoiceStatus.PAID;
    }
}
