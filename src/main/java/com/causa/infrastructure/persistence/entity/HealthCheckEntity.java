package com.causa.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Health Check JPA Entity — maps to the {@code health_checks} table.
 *
 * <p>Time-series table whose primary key is {@code created_at} (no surrogate ID by design).
 * Used for Causa engine heartbeat snapshots; rows older than 15 days are pruned by a scheduler.
 *
 * <p>{@code createdAt} shadows {@link BaseEntity#createdAt} to promote it to {@code @Id}.
 * {@code updatedAt} is inherited from {@link BaseEntity} and managed by Hibernate's
 * {@code @UpdateTimestamp} — no override needed.
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "health_checks")
public class HealthCheckEntity extends BaseEntity {

    /**
     * PK — timestamp of the snapshot. No surrogate ID: one row per heartbeat instant.
     * Shadows {@link BaseEntity#createdAt} to add {@code @Id}; set once by Hibernate via
     * {@code @CreationTimestamp} and never updated.
     */
    @Id
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Overall health status: {@code UP}, {@code DEGRADED}, or {@code DOWN}. */
    @Column(nullable = false, length = 32)
    private String overallStatus;

    /**
     * Component-level health details stored as JSONB.
     * Shape: {@code { "database": { "status": "UP", "responseTime": 12 }, "llm": { ... } }}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode componentInfo;

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }

    public JsonNode getComponentInfo() { return componentInfo; }
    public void setComponentInfo(JsonNode componentInfo) { this.componentInfo = componentInfo; }
}
