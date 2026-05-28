// =====================================================
// FILE: TenantRepository.java
// =====================================================
package com.paycycle.billing_engine.domain.repository;

import com.paycycle.billing_engine.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA — code likhne ki zaroorat nahi!
 * JpaRepository extend karo aur basic CRUD free mein milta hai:
 *   save(), findById(), findAll(), delete(), count() etc.
 *
 * Custom queries method name se hi ban jaati hain:
 *   findByDomain → SELECT * FROM tenant WHERE domain = ?
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByDomain(String domain);

    Optional<Tenant> findByApiKeyHash(String apiKeyHash);

    boolean existsByDomain(String domain);
}
