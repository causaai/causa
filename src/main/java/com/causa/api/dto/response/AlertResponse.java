package com.causa.api.dto.response;

import com.causa.core.domain.Alert;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Alert detail DTO — returned by GET /api/v1/alerts.
 *
 * <p>All fields present in the DB schema are exposed; {@code alert_metadata} fields
 * (labels, annotations, alert_source) are included in full.
 *
 * @since 0.0.1
 */
public record AlertResponse(

    @JsonProperty("id")
    String alertId,

    @JsonProperty("diag_ids")
    List<String> diagIds,

    @JsonProperty("source_alert_id")
    String sourceAlertId,

    @JsonProperty("alert_name")
    String alertName,

    @JsonProperty("alert_timestamp")
    Instant alertTimestamp,

    @JsonProperty("severity")
    String severity,

    @JsonProperty("status")
    String status,

    @JsonProperty("rejection_reason")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String rejectionReason,

    @JsonProperty("workload_info")
    WorkloadInfoDto workloadInfo,

    @JsonProperty("workload_name")
    String workloadName,

    @JsonProperty("labels")
    Map<String, String> labels,

    @JsonProperty("annotations")
    Map<String, String> annotations,

    @JsonProperty("alert_source")
    String alertSource
) {

    public record WorkloadInfoDto(
        @JsonProperty("pod_name")      String podName,
        @JsonProperty("container_name") String containerName,
        @JsonProperty("namespace")     String namespace,
        @JsonProperty("cluster_name")  String clusterName,
        @JsonProperty("workload_type") String workloadType
    ) {}

    public static AlertResponse from(Alert a) {
        Alert.WorkloadInfo wi = a.getWorkloadInfo();
        Alert.AlertMetadata am = a.getAlertMetadata();

        return new AlertResponse(
            a.getAlertId(),
            a.getDiagnosticIds(),
            a.getSourceAlertId(),
            a.getAlertName(),
            a.getAlertTimestamp(),
            a.getSeverity() != null ? a.getSeverity().getValue() : null,
            a.getStatus()   != null ? a.getStatus().getValue()   : null,
            am.rejectionReason(),
            new WorkloadInfoDto(wi.podName(), wi.containerName(), wi.namespace(), wi.clusterName(), wi.workloadType()),
            a.getWorkloadName(),
            am.labels(),
            am.annotations(),
            am.alertSource()
        );
    }
}
