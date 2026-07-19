package com.paycycle.billing_engine.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * TENANT — Multi-tenancy ka root.
 *
 * Analogy: Ek building mein kai dukaan hain.
 * Har dukaan (tenant) apna data alag rakhti hai.
 * PayCycle ek hi deployment mein kai companies ko
 * serve kar sakta hai — ye Tenant entity isliye hai.
 *
 * Example tenants:
 *   - "Netflix India" — apne subscribers ke liye
 *   - "Spotify" — apne subscribers ke liye
 * Dono ek hi PayCycle system use karenge but data alag.
 */
@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"apiKeyHash"}) // security — key print na ho logs mein
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // Unique subdomain — "netflix.paycycle.com"
    @Column(name = "domain", nullable = false, unique = true, length = 100)
    private String domain;

    // API key ka SHA-256 hash store hota hai — raw key kabhi nahi
    @Column(name = "api_key_hash", nullable = false, unique = true, length = 128)
    private String apiKeyHash;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
