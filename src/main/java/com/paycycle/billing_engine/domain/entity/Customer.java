package com.paycycle.billing_engine.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * CUSTOMER — Jo log subscribe karte hain.
 *
 * Important design decisions:
 * 1. Email UNIQUE hai per-tenant basis par (not globally)
 *    — same email alag tenants mein ho sakta hai
 * 2. tenant_id har query mein filter hoga — data isolation
 * 3. @ManyToOne tenant ke saath — N customers, 1 tenant
 */
@Entity
@Table(
    name = "customer",
    uniqueConstraints = {
        // Ek tenant mein ek hi email allowed
        @UniqueConstraint(
            name = "uq_customer_tenant_email",
            columnNames = {"tenant_id", "email"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseEntity {

    // Many customers → One tenant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Email
    @NotBlank
    @Column(name = "email", nullable = false, length = 200)
    private String email;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
