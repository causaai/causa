package com.causa.infrastructure.persistence.entity;

import jakarta.persistence.*;

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

    @Id
    @Column(name = "alert_id", nullable = false, length = 512)
    private String alertId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "alert_name", nullable = false, length = 255)
    private String alertName;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "pod_name", length = 255)
    private String podName;

    @Column(name = "container_name", nullable = false, length = 255)
    private String containerName;

    @Column(name = "namespace", nullable = false, length = 255)
    private String namespace;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "has_diagnostics", nullable = false)
    private Boolean hasDiagnostics = false;

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
}
