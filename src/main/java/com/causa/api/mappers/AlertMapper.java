package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.utils.IdUtils;
import com.causa.config.AppConfig;
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

    private final AppConfig appConfig;

    @Inject
    public AlertMapper(AppConfig appConfig) {
        this.appConfig = appConfig;
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
        String severityStr = labels.getOrDefault(AlertConstants.Labels.SEVERITY, appConfig.getAlertConfig().getFilterSeverity());

        // Workload fields — standard Prometheus labels, direct label lookup
        String containerName = labels.get(AlertConstants.Labels.CONTAINER);
        String podName       = labels.get(AlertConstants.Labels.POD);
        String namespace     = labels.get(AlertConstants.Labels.NAMESPACE);
        String clusterName   = labels.get(AlertConstants.Labels.CLUSTER_NAME);
        String workloadType  = labels.get(AlertConstants.Labels.WORKLOAD_TYPE);

        // workload_name — always set as annotation by PrometheusRule
        String workloadName  = annotations.get(AlertConstants.Labels.WORKLOAD_NAME);

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
            .workloadName(workloadName != null ? workloadName : (containerName != null ? containerName : ""))
            .alertMetadata(AlertMetadata.of(labels, annotations, alertSource))
            .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Instant parseTimestamp(String iso) {
        if (iso == null || iso.isBlank()) return Instant.now();
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return Instant.now();
        }
    }

}
