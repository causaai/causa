package com.causa.core.domain;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Alert Domain Model
 *
 * <p>Represents a single memory or GC alert associated with a Kubernetes container.
 * <p>This is an immutable aggregate root in the core domain layer.
 *
 * @since 0.0.1
 */
public final class Alert {

    private final String alertId;
    private final Instant timestamp;
    private final String alertName;
    private final AlertSeverity severity;
    private final String podName;
    private final String containerName;
    private final String namespace;
    private final AlertStatus status;
    private final boolean hasDiagnostics;
    private final Map<String, String> labels;
    private final Map<String, String> annotations;

    private Alert(Builder builder) {
        this.alertId = Objects.requireNonNull(builder.alertId, "alertId cannot be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp cannot be null");
        this.alertName = Objects.requireNonNull(builder.alertName, "alertName cannot be null");
        this.severity = Objects.requireNonNull(builder.severity, "severity cannot be null");
        this.podName = builder.podName;  // nullable for non-pod alerts
        this.containerName = builder.containerName;  // nullable
        this.namespace = Objects.requireNonNull(builder.namespace, "namespace cannot be null");
        this.status = Objects.requireNonNull(builder.status, "status cannot be null");
        this.hasDiagnostics = builder.hasDiagnostics;
        this.labels = builder.labels != null ? Collections.unmodifiableMap(builder.labels) : Map.of();
        this.annotations = builder.annotations != null ? Collections.unmodifiableMap(builder.annotations) : Map.of();
    }

    // Getters

    public String getAlertId() {
        return alertId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getAlertName() {
        return alertName;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public String getPodName() {
        return podName;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getNamespace() {
        return namespace;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public boolean hasDiagnostics() {
        return hasDiagnostics;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    /**
     * Generates a deterministic alert ID from container name and timestamp.
     *
     * <p>Format: {containerName}-{epochMillis}
     *
     * @param containerName the container name (sanitized if null)
     * @param timestamp the alert timestamp
     * @return the generated alert ID
     */
    public static String generateAlertId(String containerName, Instant timestamp) {
        String sanitized = (containerName != null && !containerName.isBlank())
            ? containerName
            : "unknown";
        return sanitized + "-" + timestamp.toEpochMilli();
    }

    /**
     * Returns the cooldown key used to deduplicate repeat alerts.
     *
     * <p>Key format: {alertName}:{podName}
     * <p>If podName is null, uses namespace instead.
     *
     * @return the cooldown deduplication key
     */
    public String getCooldownKey() {
        return alertName + ":" + (podName != null ? podName : namespace);
    }

    /**
     * Creates a new builder for constructing Alert instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing immutable Alert instances.
     */
    public static final class Builder {
        private String alertId;
        private Instant timestamp;
        private String alertName;
        private AlertSeverity severity;
        private String podName;
        private String containerName;
        private String namespace;
        private AlertStatus status;
        private boolean hasDiagnostics = false;
        private Map<String, String> labels;
        private Map<String, String> annotations;

        private Builder() {}

        public Builder alertId(String alertId) {
            this.alertId = alertId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder alertName(String alertName) {
            this.alertName = alertName;
            return this;
        }

        public Builder severity(AlertSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder podName(String podName) {
            this.podName = podName;
            return this;
        }

        public Builder containerName(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder status(AlertStatus status) {
            this.status = status;
            return this;
        }

        public Builder hasDiagnostics(boolean hasDiagnostics) {
            this.hasDiagnostics = hasDiagnostics;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder annotations(Map<String, String> annotations) {
            this.annotations = annotations;
            return this;
        }

        /**
         * Builds the Alert instance.
         *
         * @return the constructed Alert
         * @throws NullPointerException if any required field is null
         */
        public Alert build() {
            return new Alert(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Alert alert = (Alert) o;
        return Objects.equals(alertId, alert.alertId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId);
    }

    @Override
    public String toString() {
        return "Alert{" +
            "alertId='" + alertId + '\'' +
            ", timestamp=" + timestamp +
            ", alertName='" + alertName + '\'' +
            ", severity=" + severity +
            ", podName='" + podName + '\'' +
            ", containerName='" + containerName + '\'' +
            ", namespace='" + namespace + '\'' +
            ", status=" + status +
            ", hasDiagnostics=" + hasDiagnostics +
            '}';
    }
}
