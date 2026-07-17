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
 * <p>One field per schema column:
 * <pre>
 *   id               → alertId
 *   source_alert_id  → sourceAlertId   (nullable — set from Prometheus fingerprint; null if absent)
 *   alert_name       → alertName
 *   alert_timestamp  → alertTimestamp
 *   severity         → severity          (critical / warning / info)
 *   status           → status            (ACCEPTED / REJECTED / PROCESSING / PROCESSED)
 *   workload_info    → workloadInfo      JSONB: pod_name, container_name, namespace, cluster_name, workload_type
 *   workload_name    → workloadName      denormalised container name for fast index lookups
 *   alert_metadata   → alertMetadata     JSONB: labels, annotations, alert_source
 * </pre>
 *
 * @since 0.0.1
 */
public final class Alert {

    private final String alertId;
    private final String sourceAlertId;
    private final String alertName;
    private final Instant alertTimestamp;
    private final AlertSeverity severity;
    private final AlertStatus status;
    private final WorkloadInfo workloadInfo;
    private final String workloadName;
    private final AlertMetadata alertMetadata;

    private Alert(Builder builder) {
        this.alertId        = Objects.requireNonNull(builder.alertId, "alertId cannot be null");
        this.sourceAlertId  = builder.sourceAlertId;
        this.alertName      = Objects.requireNonNull(builder.alertName, "alertName cannot be null");
        this.alertTimestamp = builder.alertTimestamp;
        this.severity       = Objects.requireNonNull(builder.severity, "severity cannot be null");
        this.status         = Objects.requireNonNull(builder.status, "status cannot be null");
        this.workloadInfo   = Objects.requireNonNull(builder.workloadInfo, "workloadInfo cannot be null");
        this.workloadName   = Objects.requireNonNull(builder.workloadName, "workloadName cannot be null");
        this.alertMetadata  = builder.alertMetadata != null ? builder.alertMetadata : AlertMetadata.empty();
    }

    // -------------------------------------------------------------------------
    // Getters — one per schema column
    // -------------------------------------------------------------------------

    public String getAlertId()              { return alertId; }
    public String getSourceAlertId()        { return sourceAlertId; }
    public String getAlertName()            { return alertName; }
    public Instant getAlertTimestamp()      { return alertTimestamp; }
    public AlertSeverity getSeverity()      { return severity; }
    public AlertStatus getStatus()          { return status; }
    public WorkloadInfo getWorkloadInfo()   { return workloadInfo; }
    /** Denormalised container name stored in {@code workload_name} column for fast lookups. */
    public String getWorkloadName()         { return workloadName; }
    public AlertMetadata getAlertMetadata() { return alertMetadata; }

    /**
     * Cooldown deduplication key.
     *
     * <p>Format: {@code alertName:clusterName:namespace:workloadName:podName}
     *
     * <p>{@code alertName} and {@code workloadName} are always present — an alert without
     * a workload name is rejected by the service layer before reaching this point.
     * {@code clusterName}, {@code namespace}, and {@code podName} contribute empty string
     * when absent; the key remains distinct without artificial fallback values.
     */
    public String getCooldownKey() {
        String cluster   = orEmpty(workloadInfo.clusterName());
        String namespace = orEmpty(workloadInfo.namespace());
        String pod       = orEmpty(workloadInfo.podName());
        return alertName + ":" + cluster + ":" + namespace + ":" + workloadName + ":" + pod;
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    public static Builder builder() { return new Builder(); }

    // -------------------------------------------------------------------------
    // workload_info JSONB
    // Shape: { "pod_name": "...", "container_name": "...", "namespace": "...",
    //          "cluster_name": "...", "workload_type": "..." }
    // Values are null where not available from the incoming alert payload.
    // -------------------------------------------------------------------------

    public record WorkloadInfo(
        String podName,
        String containerName,
        String namespace,
        String clusterName,
        String workloadType
    ) {
        public static WorkloadInfo of(String podName, String containerName,
                                      String namespace, String clusterName,
                                      String workloadType) {
            return new WorkloadInfo(podName, containerName, namespace, clusterName, workloadType);
        }
    }

    // -------------------------------------------------------------------------
    // alert_metadata JSONB
    // Shape: { "labels": {...}, "annotations": {...}, "alert_source": "prometheus" }
    // alert_source defaults to "prometheus" if not present in annotations.
    // -------------------------------------------------------------------------

    public record AlertMetadata(
        Map<String, String> labels,
        Map<String, String> annotations,
        String alertSource
    ) {
        public static final String DEFAULT_SOURCE = "prometheus";

        public static AlertMetadata empty() {
            return new AlertMetadata(Map.of(), Map.of(), DEFAULT_SOURCE);
        }

        public static AlertMetadata of(Map<String, String> labels,
                                       Map<String, String> annotations,
                                       String alertSource) {
            return new AlertMetadata(
                labels      != null ? Collections.unmodifiableMap(labels)      : Map.of(),
                annotations != null ? Collections.unmodifiableMap(annotations) : Map.of(),
                alertSource != null  ? alertSource : DEFAULT_SOURCE
            );
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private String alertId;
        private String sourceAlertId;
        private String alertName;
        private Instant alertTimestamp;
        private AlertSeverity severity;
        private AlertStatus status;
        private WorkloadInfo workloadInfo;
        private String workloadName;
        private AlertMetadata alertMetadata;

        private Builder() {}

        public Builder alertId(String v)              { this.alertId = v; return this; }
        public Builder sourceAlertId(String v)        { this.sourceAlertId = v; return this; }
        public Builder alertName(String v)            { this.alertName = v; return this; }
        public Builder alertTimestamp(Instant v)      { this.alertTimestamp = v; return this; }
        public Builder severity(AlertSeverity v)      { this.severity = v; return this; }
        public Builder status(AlertStatus v)          { this.status = v; return this; }
        public Builder workloadInfo(WorkloadInfo v)   { this.workloadInfo = v; return this; }
        public Builder workloadName(String v)         { this.workloadName = v; return this; }
        public Builder alertMetadata(AlertMetadata v) { this.alertMetadata = v; return this; }

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
        return "Alert{alertId='" + alertId + "', alertName='" + alertName
            + "', severity=" + severity + ", status=" + status
            + ", workloadInfo=" + workloadInfo + ", workloadName='" + workloadName + "'}";
    }
}
