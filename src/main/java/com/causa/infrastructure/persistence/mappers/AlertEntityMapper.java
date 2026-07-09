package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.utils.JsonUtils;
import com.causa.core.domain.Alert;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Alert Entity Mapper
 *
 * <p>Maps between Alert domain model and AlertEntity JPA entity.
 *
 * <p><b>Column mapping:</b>
 * <ul>
 *   <li>{@code id}              — application-generated PK ({@code alrt_<16>})</li>
 *   <li>{@code source_alert_id} — Prometheus fingerprint</li>
 *   <li>{@code alert_name}      — Prometheus {@code alertname} label</li>
 *   <li>{@code alert_timestamp} — Prometheus {@code startsAt}</li>
 *   <li>{@code severity}        — Prometheus {@code severity} label</li>
 *   <li>{@code status}          — Causa lifecycle: ACCEPTED / REJECTED / PROCESSING / PROCESSED</li>
 *   <li>{@code container_info}  — JSON: {@code name, pod, namespace, clusterName}</li>
 *   <li>{@code container_name}  — denormalised container name for fast index lookups</li>
 *   <li>{@code alert_metadata}  — JSON: {@code prometheus_status, fingerprint, endsAt,
 *       generatorURL, labels, annotations, rejection_reason?}</li>
 * </ul>
 *
 * @since 0.0.1
 */
public final class AlertEntityMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Causa processing lifecycle status values — match the DB status column
    public static final String STATUS_ACCEPTED   = "ACCEPTED";
    public static final String STATUS_REJECTED   = "REJECTED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED  = "PROCESSED";

    private AlertEntityMapper() {}

    /**
     * Converts a domain Alert to an AlertEntity with {@code ACCEPTED} processing status.
     */
    public static AlertEntity toEntity(Alert alert) {
        return toEntityWithStatus(alert, STATUS_ACCEPTED, null);
    }

    /**
     * Converts a domain Alert to an AlertEntity with an explicit Causa processing status.
     *
     * @param alert            the domain alert
     * @param processingStatus Causa lifecycle status (ACCEPTED / REJECTED / PROCESSING / PROCESSED)
     * @param rejectionReason  optional reason stored in {@code alert_metadata}; null for non-rejected alerts
     */
    public static AlertEntity toEntityWithStatus(Alert alert, String processingStatus, String rejectionReason) {
        if (alert == null) return null;

        AlertEntity entity = new AlertEntity();

        // PK and source identifier
        entity.setId(alert.getAlertId());
        entity.setSourceAlertId(
            alert.getSourceAlertId() != null ? alert.getSourceAlertId() : alert.getAlertId());
        entity.setAlertName(alert.getAlertName());

        // Timestamp
        if (alert.getTimestamp() != null) {
            entity.setAlertTimestamp(alert.getTimestamp().atOffset(ZoneOffset.UTC));
        }

        // Severity + Causa lifecycle status
        entity.setSeverity(alert.getSeverity() != null ? alert.getSeverity().getValue() : null);
        entity.setStatus(processingStatus);
        entity.setContainerName(alert.getContainerName() != null ? alert.getContainerName() : "");

        // container_info JSONB: name, pod, namespace, clusterName
        ObjectNode containerInfo = objectMapper.createObjectNode();
        containerInfo.put("name",        alert.getContainerName() != null ? alert.getContainerName() : "");
        containerInfo.put("pod",         alert.getPodName()       != null ? alert.getPodName()       : "");
        containerInfo.put("namespace",   alert.getNamespace()     != null ? alert.getNamespace()     : "");
        containerInfo.put("clusterName", "");   // populated by future cluster-aware context
        entity.setContainerInfo(containerInfo);

        // alert_metadata JSONB: prometheus_status, fingerprint, endsAt, generatorURL, labels, annotations, rejection_reason?
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("prometheus_status",
            alert.getPrometheusStatus() != null ? alert.getPrometheusStatus().getValue() : null);
        metadata.put("fingerprint",   alert.getFingerprint());
        metadata.put("endsAt",        alert.getEndsAt());
        metadata.put("generatorURL",  alert.getGeneratorURL());
        metadata.set("labels",        JsonUtils.mapToJsonNode(alert.getLabels()));
        metadata.set("annotations",   JsonUtils.mapToJsonNode(alert.getAnnotations()));
        if (rejectionReason != null) {
            metadata.put("rejection_reason", rejectionReason);
        }
        entity.setAlertMetadata(metadata);

        return entity;
    }

    /**
     * Converts an AlertEntity back to a domain Alert.
     */
    public static Alert toDomain(AlertEntity entity) {
        if (entity == null) return null;

        // Unpack container_info JSONB
        String containerName = entity.getContainerName();
        String pod           = null;
        String namespace     = null;
        if (entity.getContainerInfo() != null) {
            containerName = entity.getContainerInfo().path("name").asText(containerName);
            pod           = entity.getContainerInfo().path("pod").asText(null);
            namespace     = entity.getContainerInfo().path("namespace").asText(null);
        }

        // Unpack alert_metadata JSONB
        java.util.Map<String, String> labels      = null;
        java.util.Map<String, String> annotations = null;
        String prometheusStatusStr = null;
        String fingerprint         = null;
        String endsAt              = null;
        String generatorURL        = null;
        String rejectionReason     = null;
        if (entity.getAlertMetadata() != null) {
            labels             = JsonUtils.jsonNodeToMap(entity.getAlertMetadata().get("labels"));
            annotations        = JsonUtils.jsonNodeToMap(entity.getAlertMetadata().get("annotations"));
            prometheusStatusStr = entity.getAlertMetadata().path("prometheus_status").asText(null);
            fingerprint        = entity.getAlertMetadata().path("fingerprint").asText(null);
            endsAt             = entity.getAlertMetadata().path("endsAt").asText(null);
            generatorURL       = entity.getAlertMetadata().path("generatorURL").asText(null);
            rejectionReason    = entity.getAlertMetadata().path("rejection_reason").asText(null);
        }

        // Restore Prometheus firing/resolved status; fall back to FIRING if missing
        AlertStatus prometheusStatus;
        try {
            prometheusStatus = (prometheusStatusStr != null && !prometheusStatusStr.isBlank())
                ? AlertStatus.fromString(prometheusStatusStr)
                : AlertStatus.FIRING;
        } catch (IllegalArgumentException e) {
            prometheusStatus = AlertStatus.FIRING;
        }

        // OffsetDateTime → Instant
        Instant timestamp = entity.getAlertTimestamp() != null
            ? entity.getAlertTimestamp().toInstant()
            : null;

        return Alert.builder()
            .alertId(entity.getId())
            .sourceAlertId(entity.getSourceAlertId())
            .timestamp(timestamp)
            .alertName(entity.getAlertName())
            .severity(AlertSeverity.fromString(entity.getSeverity()))
            .podName(pod)
            .containerName(containerName)
            .namespace(namespace)
            .prometheusStatus(prometheusStatus)
            .processingStatus(entity.getStatus())   // ACCEPTED / REJECTED / PROCESSING / PROCESSED
            .hasDiagnostics(false)
            .labels(labels)
            .annotations(annotations)
            .fingerprint(fingerprint)
            .endsAt(endsAt)
            .generatorURL(generatorURL)
            .build();
    }
}
