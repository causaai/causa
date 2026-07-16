package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.config.AlertConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Alert.AlertMetadata;
import com.causa.core.domain.Alert.WorkloadInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Alert Mapper
 *
 * <p>Maps Prometheus Alertmanager webhook DTOs to domain {@link Alert} objects.
 *
 * <p>All workload fields ({@code container_name}, {@code pod_name}, {@code namespace},
 * {@code cluster_name}, {@code workload_type}) and {@code alert_source} are extracted
 * from annotations first, then labels as fallback. Values remain {@code null} when
 * not present in either.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertMapper {

    private final String defaultSeverity;

    @Inject
    public AlertMapper(AlertConfig alertConfig) {
        this.defaultSeverity = alertConfig.filterSeverity();
    }

    public List<Alert> toDomainList(AlertWebhookRequest request) {
        if (request == null || request.getAlerts() == null) {
            return List.of();
        }
        return request.getAlerts().stream()
            .map(this::toDomain)
            .toList();
    }

    public Alert toDomain(AlertWebhookRequest.AlertItem item) {
        Map<String, String> labels      = item.getLabels()      != null ? item.getLabels()      : Map.of();
        Map<String, String> annotations = item.getAnnotations() != null ? item.getAnnotations() : Map.of();

        // Core alert fields — from labels
        String alertName   = labels.get(AlertConstants.Labels.ALERT_NAME);
        String severityStr = labels.getOrDefault(AlertConstants.Labels.SEVERITY, defaultSeverity);

        // Workload fields — annotations first (as declared in PrometheusRule), labels as fallback
        String containerName = getWithFallback(annotations, labels, AlertConstants.Labels.CONTAINER);
        String podName       = getWithFallback(annotations, labels, AlertConstants.Labels.POD);
        String namespace     = getWithFallback(annotations, labels, AlertConstants.Labels.NAMESPACE);
        String clusterName   = getWithFallback(annotations, labels, AlertConstants.Labels.CLUSTER_NAME);
        String workloadType  = getWithFallback(annotations, labels, AlertConstants.Labels.WORKLOAD_TYPE);

        // alert_source — from annotations, default to "prometheus"
        String alertSource = annotations.getOrDefault(
            AlertConstants.Labels.ALERT_SOURCE, AlertMetadata.DEFAULT_SOURCE);

        Instant timestamp  = parseTimestamp(item.getStartsAt());
        String fingerprint = item.getFingerprint();
        String alertId     = generateAlertId(containerName, timestamp);

        return Alert.builder()
            .alertId(alertId)
            .sourceAlertId(fingerprint != null ? fingerprint : alertId)
            .alertName(alertName)
            .alertTimestamp(timestamp)
            .severity(AlertSeverity.fromString(severityStr))
            .status(AlertStatus.PROCESSING)   // initial Causa status; updated by service layer
            .workloadInfo(WorkloadInfo.of(podName, containerName, namespace, clusterName, workloadType))
            .workloadName(containerName != null ? containerName : "")
            .alertMetadata(AlertMetadata.of(labels, annotations, alertSource))
            .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Checks primary map first, falls back to secondary. Returns null if absent in both. */
    private String getWithFallback(Map<String, String> primary, Map<String, String> secondary, String key) {
        String value = primary.get(key);
        return value != null ? value : secondary.get(key);
    }

    private Instant parseTimestamp(String iso) {
        if (iso == null || iso.isBlank()) return Instant.now();
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String generateAlertId(String containerName, Instant timestamp) {
        String safe = (containerName != null && !containerName.isBlank()) ? containerName : "unknown";
        return safe + "-" + timestamp.toEpochMilli();
    }
}
