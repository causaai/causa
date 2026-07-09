package com.causa.api.dto.response;

import com.causa.core.domain.Alert;

import java.time.Instant;
import java.util.Map;

/**
 * Alert Detail Response DTO
 *
 * <p>Returned by {@code GET /api/v1/alerts} with full alert details.
 *
 * @param alertId           application-generated alert ID ({@code alrt_<16>})
 * @param sourceAlertId     Prometheus fingerprint (source system identifier)
 * @param alertName         Prometheus {@code alertname} label
 * @param prometheusStatus  Prometheus alert lifecycle: {@code firing} or {@code resolved}
 * @param processingStatus  Causa lifecycle: {@code ACCEPTED}, {@code REJECTED}, {@code PROCESSING}, {@code PROCESSED}
 * @param severity          alert severity: {@code critical} / {@code warning} / {@code info}
 * @param namespace         Kubernetes namespace
 * @param podName           Kubernetes pod name (nullable)
 * @param containerName     Kubernetes container name
 * @param timestamp         when the alert fired ({@code startsAt} from Prometheus)
 * @param hasDiagnostics    whether diagnostics have been triggered for this alert
 * @param fingerprint       Prometheus fingerprint (same as sourceAlertId)
 * @param endsAt            Prometheus {@code endsAt} timestamp string
 * @param generatorURL      Prometheus alert generator URL
 * @param labels            raw Prometheus labels
 * @param annotations       raw Prometheus annotations
 *
 * @since 0.0.1
 */
public record AlertDetailResponse(
    String alertId,
    String sourceAlertId,
    String alertName,
    String prometheusStatus,
    String processingStatus,
    String severity,
    String namespace,
    String podName,
    String containerName,
    Instant timestamp,
    boolean hasDiagnostics,
    String fingerprint,
    String endsAt,
    String generatorURL,
    Map<String, String> labels,
    Map<String, String> annotations
) {

    /**
     * Maps a domain {@link Alert} to an {@code AlertDetailResponse}.
     */
    public static AlertDetailResponse from(Alert alert) {
        return new AlertDetailResponse(
            alert.getAlertId(),
            alert.getSourceAlertId(),
            alert.getAlertName(),
            alert.getPrometheusStatus()  != null ? alert.getPrometheusStatus().getValue()  : null,
            alert.getProcessingStatus(),
            alert.getSeverity()          != null ? alert.getSeverity().getValue()          : null,
            alert.getNamespace(),
            alert.getPodName(),
            alert.getContainerName(),
            alert.getTimestamp(),
            alert.hasDiagnostics(),
            alert.getFingerprint(),
            alert.getEndsAt(),
            alert.getGeneratorURL(),
            alert.getLabels(),
            alert.getAnnotations()
        );
    }
}
