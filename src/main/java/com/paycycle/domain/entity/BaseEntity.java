package com.paycycle.billing_engine.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * BaseEntity — Sabhi entities ka parent class.
 *
 * Yahan 4 common fields hain jo EVERY table mein hote hain:
 *   - id          : UUID primary key (auto-generate)
 *   - createdAt   : kab create hua (automatic)
 *   - updatedAt   : kab update hua (automatic)
 *
 * @MappedSuperclass matlab: ye khud koi table nahi banta,
 * but jo bhi class extend kare usse ye fields mil jaate hain.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(jakarta.persistence.PrePersist.class)
public abstract class BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Ye method automatically chalega SAVE karne se pehle
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Ye method automatically chalega UPDATE karne se pehle
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
