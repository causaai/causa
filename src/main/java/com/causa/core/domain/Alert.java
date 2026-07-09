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
 * <p>Represents a single alert received from Prometheus Alertmanager.
 *
 * <p><b>ID semantics:</b>
 * <ul>
 *   <li>{@code alertId}       — application-generated PK: {@code alrt_<16>}</li>
 *   <li>{@code sourceAlertId} — Prometheus fingerprint (or alertname if fingerprint absent)</li>
 * </ul>
 *
 * <p><b>Status semantics:</b>
 * <ul>
 *   <li>{@code prometheusStatus} — Prometheus lifecycle: {@code firing} / {@code resolved}</li>
 *   <li>{@code processingStatus} — Causa lifecycle: {@code ACCEPTED} / {@code REJECTED} /
 *       {@code PROCESSING} / {@code PROCESSED}</li>
 * </ul>
 *
 * @since 0.0.1
 */
public final class Alert {

    private final String alertId;
    private final String sourceAlertId;       // Prometheus fingerprint
    private final Instant timestamp;
    private final String alertName;
    private final AlertSeverity severity;
    private final String podName;
    private final String containerName;
    private final String namespace;
    private final AlertStatus prometheusStatus;   // firing / resolved
    private final String processingStatus;        // ACCEPTED / REJECTED / PROCESSING / PROCESSED
    private final boolean hasDiagnostics;
    private final Map<String, String> labels;
    private final Map<String, String> annotations;

    // Extra Prometheus payload fields stored in alert_metadata
    private final String fingerprint;
    private final String endsAt;
    private final String generatorURL;

    private Alert(Builder builder) {
        this.alertId          = Objects.requireNonNull(builder.alertId, "alertId cannot be null");
        this.sourceAlertId    = builder.sourceAlertId;
        this.timestamp        = Objects.requireNonNull(builder.timestamp, "timestamp cannot be null");
        this.alertName        = Objects.requireNonNull(builder.alertName, "alertName cannot be null");
        this.severity         = Objects.requireNonNull(builder.severity, "severity cannot be null");
        this.podName          = builder.podName;
        this.containerName    = builder.containerName;
        this.namespace        = Objects.requireNonNull(builder.namespace, "namespace cannot be null");
        this.prometheusStatus = builder.prometheusStatus;
        this.processingStatus = builder.processingStatus;
        this.hasDiagnostics   = builder.hasDiagnostics;
        this.labels           = builder.labels      != null ? Collections.unmodifiableMap(builder.labels)      : Map.of();
        this.annotations      = builder.annotations != null ? Collections.unmodifiableMap(builder.annotations) : Map.of();
        this.fingerprint      = builder.fingerprint;
        this.endsAt           = builder.endsAt;
        this.generatorURL     = builder.generatorURL;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getAlertId()           { return alertId; }
    public String getSourceAlertId()     { return sourceAlertId; }
    public Instant getTimestamp()        { return timestamp; }
    public String getAlertName()         { return alertName; }
    public AlertSeverity getSeverity()   { return severity; }
    public String getPodName()           { return podName; }
    public String getContainerName()     { return containerName; }
    public String getNamespace()         { return namespace; }

    /** Prometheus alert lifecycle: {@code firing} or {@code resolved}. */
    public AlertStatus getPrometheusStatus()  { return prometheusStatus; }

    /** Causa processing lifecycle: {@code ACCEPTED}, {@code REJECTED}, {@code PROCESSING}, {@code PROCESSED}. */
    public String getProcessingStatus()  { return processingStatus; }

    /**
     * @deprecated Use {@link #getPrometheusStatus()} for Prometheus status or
     *             {@link #getProcessingStatus()} for Causa lifecycle status.
     */
    @Deprecated
    public AlertStatus getStatus()       { return prometheusStatus; }

    public boolean hasDiagnostics()      { return hasDiagnostics; }
    public Map<String, String> getLabels()      { return labels; }
    public Map<String, String> getAnnotations() { return annotations; }
    public String getFingerprint()       { return fingerprint; }
    public String getEndsAt()            { return endsAt; }
    public String getGeneratorURL()      { return generatorURL; }

    /**
     * Returns the cooldown key used to deduplicate repeat alerts.
     * Format: {@code {alertName}:{podName}} or {@code {alertName}:{namespace}} if pod is null.
     */
    public String getCooldownKey() {
        return alertName + ":" + (podName != null ? podName : namespace);
    }

    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private String alertId;
        private String sourceAlertId;
        private Instant timestamp;
        private String alertName;
        private AlertSeverity severity;
        private String podName;
        private String containerName;
        private String namespace;
        private AlertStatus prometheusStatus;
        private String processingStatus;
        private boolean hasDiagnostics = false;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String fingerprint;
        private String endsAt;
        private String generatorURL;

        private Builder() {}

        public Builder alertId(String alertId)               { this.alertId = alertId; return this; }
        public Builder sourceAlertId(String sourceAlertId)   { this.sourceAlertId = sourceAlertId; return this; }
        public Builder timestamp(Instant timestamp)          { this.timestamp = timestamp; return this; }
        public Builder alertName(String alertName)           { this.alertName = alertName; return this; }
        public Builder severity(AlertSeverity severity)      { this.severity = severity; return this; }
        public Builder podName(String podName)               { this.podName = podName; return this; }
        public Builder containerName(String containerName)   { this.containerName = containerName; return this; }
        public Builder namespace(String namespace)           { this.namespace = namespace; return this; }
        public Builder prometheusStatus(AlertStatus s)       { this.prometheusStatus = s; return this; }
        public Builder processingStatus(String s)            { this.processingStatus = s; return this; }
        /** @deprecated Use {@link #prometheusStatus(AlertStatus)} */
        @Deprecated
        public Builder status(AlertStatus status)            { this.prometheusStatus = status; return this; }
        public Builder hasDiagnostics(boolean v)             { this.hasDiagnostics = v; return this; }
        public Builder labels(Map<String, String> labels)    { this.labels = labels; return this; }
        public Builder annotations(Map<String, String> a)    { this.annotations = a; return this; }
        public Builder fingerprint(String fingerprint)       { this.fingerprint = fingerprint; return this; }
        public Builder endsAt(String endsAt)                 { this.endsAt = endsAt; return this; }
        public Builder generatorURL(String generatorURL)     { this.generatorURL = generatorURL; return this; }

        public Alert build() { return new Alert(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(alertId, ((Alert) o).alertId);
    }

    @Override
    public int hashCode() { return Objects.hash(alertId); }

    @Override
    public String toString() {
        return "Alert{alertId='" + alertId + "', alertName='" + alertName + "', severity=" + severity
            + ", namespace='" + namespace + "', podName='" + podName + "', processingStatus='" + processingStatus + "'}";
    }
}
