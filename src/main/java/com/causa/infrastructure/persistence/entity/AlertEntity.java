package com.causa.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Alert JPA Entity — maps to the {@code alerts} table.
 *
 * <p>Column mapping:
 * <pre>
 *   id               ← id
 *   source_alert_id  ← sourceAlertId
 *   alert_name       ← alertName
 *   alert_timestamp  ← alertTimestamp
 *   severity         ← severity
 *   status           ← status           (ACCEPTED / REJECTED / PROCESSING / PROCESSED)
 *   workload_info    ← workloadInfo     JSONB: pod_name, container_name, namespace, cluster_name, workload_type
 *   workload_name    ← workloadName     denormalised container name for fast index lookups
 *   alert_metadata   ← alertMetadata   JSONB: labels, annotations, alert_source
 * </pre>
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "alerts")
public class AlertEntity extends BaseEntity {

    @Id
    @Column(nullable = false, length = 21)
    private String id;

    @Column(length = 255)
    private String sourceAlertId;

    @Column(nullable = false, length = 255)
    private String alertName;

    @Column
    private OffsetDateTime alertTimestamp;

    @Column(length = 32)
    private String severity;

    /** ACCEPTED / REJECTED / PROCESSING / PROCESSED */
    @Column(nullable = false, length = 32)
    private String status;

    /**
     * workload_info JSONB.
     * Shape: {@code { "pod_name": "...", "container_name": "...", "namespace": "...",
     *                  "cluster_name": "...", "workload_type": "..." }}
     * Values are null where not available from the alert payload.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workload_info", nullable = false, columnDefinition = "jsonb")
    private JsonNode workloadInfo;

    /** Denormalised container name for fast indexed lookups (maps to {@code workload_name} column). */
    @Column(nullable = false, length = 255)
    private String workloadName;

    /**
     * alert_metadata JSONB.
     * Shape: {@code { "labels": {...}, "annotations": {...}, "alert_source": "prometheus" }}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode alertMetadata;

    // -------------------------------------------------------------------------
    // HQL field name constants — use in Panache queries to avoid magic strings
    // -------------------------------------------------------------------------

    public static final class Fields {
        private Fields() {}

        public static final String ALERT_ID      = "id";
        public static final String ALERT_NAME    = "alertName";
        public static final String STATUS        = "status";
        public static final String WORKLOAD_NAME = "workloadName";
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceAlertId() { return sourceAlertId; }
    public void setSourceAlertId(String sourceAlertId) { this.sourceAlertId = sourceAlertId; }

    public String getAlertName() { return alertName; }
    public void setAlertName(String alertName) { this.alertName = alertName; }

    public OffsetDateTime getAlertTimestamp() { return alertTimestamp; }
    public void setAlertTimestamp(OffsetDateTime alertTimestamp) { this.alertTimestamp = alertTimestamp; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public JsonNode getWorkloadInfo() { return workloadInfo; }
    public void setWorkloadInfo(JsonNode v) { this.workloadInfo = v; }

    public String getWorkloadName() { return workloadName; }
    public void setWorkloadName(String v) { this.workloadName = v; }

    public JsonNode getAlertMetadata() { return alertMetadata; }
    public void setAlertMetadata(JsonNode alertMetadata) { this.alertMetadata = alertMetadata; }
}
