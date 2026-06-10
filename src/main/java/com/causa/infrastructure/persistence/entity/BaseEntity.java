package com.causa.infrastructure.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Base Entity
 *
 * <p>Abstract base class for all JPA entities. Extends {@link PanacheEntity} to
 * inherit the auto-generated {@code id} field and provides automatic timestamp
 * tracking for creation and last update.
 *
 * <p>The {@code createdAt} and {@code updatedAt} fields are automatically managed
 * by Hibernate using {@link CreationTimestamp} and {@link UpdateTimestamp} annotations.
 *
 * @since 1.0.0
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntity {

    /**
     * Timestamp when the entity was created.
     *
     * <p>Automatically set by Hibernate on first persist. Immutable after creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    /**
     * Timestamp when the entity was last updated.
     *
     * <p>Automatically updated by Hibernate on every merge/update operation.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
