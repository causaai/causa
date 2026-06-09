package com.causa.infrastructure.persistence.entity;

import com.causa.common.constants.AppConstants;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * System Health History Entity
 *
 * <p>JPA entity for tracking system health snapshots over time. Stores overall
 * system health status and detailed component metrics as JSON.
 *
 * <p>The {@code componentMetrics} field is stored as JSONB in PostgreSQL for
 * efficient querying and indexing of nested JSON data.
 *
 * <p>Extends {@link BaseEntity} to inherit:
 * <ul>
 *   <li>{@code id} - BIGSERIAL primary key (from PanacheEntity)</li>
 *   <li>{@code createdAt} - automatic creation timestamp</li>
 *   <li>{@code updatedAt} - automatic update timestamp</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Entity
@Table(name = "system_health_history")
public class SystemHealthHistoryEntity extends BaseEntity {

    /**
     * Timestamp of the health snapshot.
     *
     * <p>Represents when this health check was performed.
     */
    @NotNull(message = "Timestamp cannot be null")
    @Column(name = "timestamp", nullable = false)
    public LocalDateTime timestamp;

    /**
     * Overall system health status.
     *
     * <p>Stored as VARCHAR in database using enum name (UP, DOWN).
     */
    @NotNull(message = "Overall status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false)
    public AppConstants.HealthStatus overallStatus;

    /**
     * Component-level metrics stored as JSON.
     *
     * <p>Example structure:
     * <pre>
     * {
     *   "database": {"status": "UP", "responseTime": 12},
     *   "llm": {"status": "UP", "provider": "claude"},
     *   "mcp": {"status": "UP", "servers": ["kubernetes", "cryostat"]}
     * }
     * </pre>
     *
     * <p>Can be null if only overall status is being tracked.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_metrics", columnDefinition = "jsonb")
    public JsonNode componentMetrics;
}
