package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.utils.JsonUtils;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Alert.AlertMetadata;
import com.causa.core.domain.Alert.WorkloadInfo;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.ZoneOffset;
import java.util.Map;

/**
 * Alert Entity Mapper
 *
 * <p>Maps between the {@link Alert} domain model and {@link AlertEntity} JPA entity.
 *
 * <p>Column mapping:
 * <pre>
 *   workload_info  JSONB  ← { pod_name, container_name, namespace, cluster_name, workload_type }
 *   workload_name         ← workloadInfo.containerName  (denormalised for index lookups)
 *   alert_metadata JSONB  ← { labels, annotations, alert_source }
 * </pre>
 *
 * @since 0.0.1
 */
public final class AlertEntityMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String STATUS_ACCEPTED   = "ACCEPTED";
    public static final String STATUS_REJECTED   = "REJECTED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED  = "PROCESSED";

    private AlertEntityMapper() {}

    /**
     * Converts a domain {@link Alert} to an {@link AlertEntity} with an explicit
     * Causa processing status and optional rejection reason stored in alert_metadata.
     *
     * @param alert            the domain alert
     * @param processingStatus ACCEPTED / REJECTED / PROCESSING / PROCESSED
     * @param rejectionReason  stored under {@code alert_metadata.rejection_reason}; null for non-rejected alerts
     */
    public static AlertEntity toEntityWithStatus(Alert alert, String processingStatus, String rejectionReason) {
        if (alert == null) return null;

        AlertEntity entity = new AlertEntity();

        // Scalar columns
        entity.setId(alert.getAlertId());
        entity.setSourceAlertId(alert.getSourceAlertId());
        entity.setAlertName(alert.getAlertName());
        entity.setAlertTimestamp(alert.getAlertTimestamp() != null
            ? alert.getAlertTimestamp().atOffset(ZoneOffset.UTC) : null);
        entity.setSeverity(alert.getSeverity() != null ? alert.getSeverity().getValue() : null);
        entity.setStatus(processingStatus);

        // workload_info JSONB
        WorkloadInfo wi = alert.getWorkloadInfo();
        ObjectNode workloadNode = MAPPER.createObjectNode();
        workloadNode.put("pod_name",       wi.podName());
        workloadNode.put("container_name", wi.containerName());
        workloadNode.put("namespace",      wi.namespace());
        workloadNode.put("cluster_name",   wi.clusterName());
        workloadNode.put("workload_type",  wi.workloadType());
        entity.setWorkloadInfo(workloadNode);

        // workload_name — denormalised container name
        entity.setWorkloadName(wi.containerName() != null ? wi.containerName() : "");

        // alert_metadata JSONB
        AlertMetadata am = alert.getAlertMetadata();
        ObjectNode metaNode = MAPPER.createObjectNode();
        metaNode.set("labels",       JsonUtils.mapToJsonNode(am.labels()));
        metaNode.set("annotations",  JsonUtils.mapToJsonNode(am.annotations()));
        metaNode.put("alert_source", am.alertSource());
        if (rejectionReason != null) {
            metaNode.put("rejection_reason", rejectionReason);
        }
        entity.setAlertMetadata(metaNode);

        return entity;
    }

    /**
     * Convenience overload — uses {@code ACCEPTED} status, no rejection reason.
     */
    public static AlertEntity toEntity(Alert alert) {
        return toEntityWithStatus(alert, STATUS_ACCEPTED, null);
    }

    /**
     * Converts an {@link AlertEntity} back to a domain {@link Alert}.
     */
    public static Alert toDomain(AlertEntity entity) {
        if (entity == null) return null;

        // Unpack workload_info JSONB
        String podName       = null;
        String containerName = entity.getWorkloadName(); // fallback to denormalised column
        String namespace     = null;
        String clusterName   = null;
        String workloadType  = null;
        if (entity.getWorkloadInfo() != null) {
            var wi       = entity.getWorkloadInfo();
            podName      = wi.path("pod_name").asText(null);
            containerName = wi.path("container_name").asText(containerName);
            namespace    = wi.path("namespace").asText(null);
            clusterName  = wi.path("cluster_name").asText(null);
            workloadType = wi.path("workload_type").asText(null);
        }

        // Unpack alert_metadata JSONB
        Map<String, String> labels      = Map.of();
        Map<String, String> annotations = Map.of();
        String alertSource = AlertMetadata.DEFAULT_SOURCE;
        if (entity.getAlertMetadata() != null) {
            var am  = entity.getAlertMetadata();
            labels      = JsonUtils.jsonNodeToMap(am.get("labels"));
            annotations = JsonUtils.jsonNodeToMap(am.get("annotations"));
            alertSource = am.path("alert_source").asText(AlertMetadata.DEFAULT_SOURCE);
        }

        return Alert.builder()
            .alertId(entity.getId())
            .sourceAlertId(entity.getSourceAlertId())
            .alertName(entity.getAlertName())
            .alertTimestamp(entity.getAlertTimestamp() != null
                ? entity.getAlertTimestamp().toInstant() : null)
            .severity(AlertSeverity.fromString(entity.getSeverity()))
            .status(AlertStatus.fromString(entity.getStatus()))
            .workloadInfo(WorkloadInfo.of(podName, containerName, namespace, clusterName, workloadType))
            .workloadName(entity.getWorkloadName())
            .alertMetadata(AlertMetadata.of(labels, annotations, alertSource))
            .build();
    }

}
