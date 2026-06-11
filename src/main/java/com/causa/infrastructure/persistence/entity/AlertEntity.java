package com.causa.infrastructure.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Alert JPA Entity
 *
 * <p>Database entity for storing alerts in the persistence layer.
 * <p>Extends {@link BaseEntity} for automatic created_at/updated_at timestamps.
 *
 * @since 0.0.1
 */
@Entity
@Table(name = "alerts")
public class AlertEntity extends BaseEntity {

    // Database column name constants
    public static final class Columns {
        private Columns() {}

        public static final String ALERT_ID = "alert_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String ALERT_NAME = "alert_name";
        public static final String SEVERITY = "severity";
        public static final String POD_NAME = "pod_name";
        public static final String CONTAINER_NAME = "container_name";
        public static final String NAMESPACE = "namespace";
        public static final String STATUS = "status";
        public static final String HAS_DIAGNOSTICS = "has_diagnostics";
        public static final String LABELS = "labels";
        public static final String ANNOTATIONS = "annotations";
    }

    // Field name constants for Panache queries
    public static final class Fields {
        private Fields() {}

        public static final String ALERT_ID = "alertId";
        public static final String TIMESTAMP = "timestamp";
        public static final String CONTAINER_NAME = "containerName";
        public static final String NAMESPACE = "namespace";
        public static final String SEVERITY = "severity";
        public static final String HAS_DIAGNOSTICS = "hasDiagnostics";
        public static final String LABELS = "labels";
        public static final String ANNOTATIONS = "annotations";
    }

    @Id
    @Column(name = Columns.ALERT_ID, nullable = false, length = 512)
    private String alertId;

    @Column(name = Columns.TIMESTAMP, nullable = false)
    private Instant timestamp;

    @Column(name = Columns.ALERT_NAME, nullable = false, length = 255)
    private String alertName;

    @Column(name = Columns.SEVERITY, nullable = false, length = 20)
    private String severity;

    @Column(name = Columns.POD_NAME, length = 255)
    private String podName;

    @Column(name = Columns.CONTAINER_NAME, nullable = false, length = 255)
    private String containerName;

    @Column(name = Columns.NAMESPACE, nullable = false, length = 255)
    private String namespace;

    @Column(name = Columns.STATUS, nullable = false, length = 20)
    private String status;

    @Column(name = Columns.HAS_DIAGNOSTICS, nullable = false)
    private Boolean hasDiagnostics = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = Columns.LABELS, columnDefinition = "jsonb")
    private JsonNode labels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = Columns.ANNOTATIONS, columnDefinition = "jsonb")
    private JsonNode annotations;

    // Getters and Setters

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getAlertName() {
        return alertName;
    }

    public void setAlertName(String alertName) {
        this.alertName = alertName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getHasDiagnostics() {
        return hasDiagnostics;
    }

    public void setHasDiagnostics(Boolean hasDiagnostics) {
        this.hasDiagnostics = hasDiagnostics;
    }

    public JsonNode getLabels() {
        return labels;
    }

    public void setLabels(JsonNode labels) {
        this.labels = labels;
    }

    public JsonNode getAnnotations() {
        return annotations;
    }

    public void setAnnotations(JsonNode annotations) {
        this.annotations = annotations;
    }
}
