package com.causa.infrastructure.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Base Entity
 *
 * <p>Abstract base class for all JPA entities providing automatic {@code TIMESTAMP WITH TIME ZONE}
 * tracking via Hibernate's {@link CreationTimestamp} and {@link UpdateTimestamp}.
 *
 * @since 0.0.1
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    /** Set once on first persist; never updated. Maps to column {@code created_at}. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Updated by Hibernate on every merge. Maps to column {@code updated_at}. */
    @UpdateTimestamp
    @Column(nullable = false)
    public OffsetDateTime updatedAt;
}
