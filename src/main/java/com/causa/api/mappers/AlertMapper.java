package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.utils.IdUtils;
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

    /**
     * Maps an Alertmanager webhook request to a list of domain Alert objects.
     *
     * @param request the webhook request
     * @return list of domain Alert objects
     */

    public List<Alert> toDomainList(AlertWebhookRequest request) {
        if (request == null || request.getAlerts() == null) {
            return List.of();
        }

        return request.getAlerts().stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * Maps a single Alertmanager alert item to a domain Alert.
     *
     * <p>Validation ensures container and namespace are always present.
     * <p>Severity defaults to configured value if missing.
     * <p>Uses Prometheus fingerprint as alert ID for global uniqueness and idempotency.
     *
     * @param item the alert item from the webhook payload
     * @return the domain Alert object
     */
    public Alert toDomain(AlertWebhookRequest.AlertItem item) {
        Map<String, String> labels      = item.getLabels()      != null ? item.getLabels()      : Map.of();
        Map<String, String> annotations = item.getAnnotations() != null ? item.getAnnotations() : Map.of();

        // Core alert fields — from labels
        String alertName   = labels.get(AlertConstants.Labels.ALERT_NAME);
        String severityStr = labels.getOrDefault(AlertConstants.Labels.SEVERITY, defaultSeverity);

        // Workload fields — annotations first (PrometheusRule uses container_name / pod_name as annotation
        // keys), label keys (container / pod) used as fallback for older or custom alert sources.
        String containerName = getAnnotationOrLabel(annotations, labels,
            AlertConstants.Labels.CONTAINER_NAME, AlertConstants.Labels.CONTAINER);
        String podName       = getAnnotationOrLabel(annotations, labels,
            AlertConstants.Labels.POD_NAME, AlertConstants.Labels.POD);
        String namespace     = getAnnotationOrLabel(annotations, labels, AlertConstants.Labels.NAMESPACE,    AlertConstants.Labels.NAMESPACE);
        String clusterName   = getAnnotationOrLabel(annotations, labels, AlertConstants.Labels.CLUSTER_NAME, AlertConstants.Labels.CLUSTER_NAME);
        String workloadType  = getAnnotationOrLabel(annotations, labels, AlertConstants.Labels.WORKLOAD_TYPE, AlertConstants.Labels.WORKLOAD_TYPE);

        // alert_source — from annotations, default to "prometheus"
        String alertSource = annotations.getOrDefault(
            AlertConstants.Labels.ALERT_SOURCE, AlertMetadata.DEFAULT_SOURCE);

        Instant timestamp  = parseTimestamp(item.getStartsAt());
        String fingerprint = item.getFingerprint();
        String alertId     = IdUtils.generateAlertId();

        return Alert.builder()
            .alertId(alertId)
            .sourceAlertId(fingerprint != null && !fingerprint.isBlank() ? fingerprint : null)
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

    /**
     * Looks up {@code annotationKey} in annotations first, then {@code labelKey} in labels.
     * Pass the same key for both params when the key is identical in both maps.
     */
    private String getAnnotationOrLabel(Map<String, String> annotations, Map<String, String> labels,
                                        String annotationKey, String labelKey) {
        String value = annotations.get(annotationKey);
        return value != null ? value : labels.get(labelKey);
    }

    private Instant parseTimestamp(String iso) {
        if (iso == null || iso.isBlank()) return Instant.now();
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return Instant.now();
        }
    }

}
