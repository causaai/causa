package com.causa.api.dto.response;

import com.causa.core.domain.Alert;

import java.time.Instant;
import java.util.Map;

/**
 * Alert Detail Response DTO
 *
 * <p>Returned by the GET /api/v1/alerts endpoint with full alert details.
 *
 * @param alertId           application-generated alert identifier
 * @param alertName         human-readable alert name (e.g. {@code HighMemoryUsage})
 * @param status            Prometheus alert status (firing / resolved)
 * @param severity          alert severity (critical / warning / info)
 * @param namespace         Kubernetes namespace
 * @param podName           Kubernetes pod name (nullable)
 * @param containerName     Kubernetes container name
 * @param timestamp         when the alert was fired by Prometheus
 * @param hasDiagnostics    whether diagnostics have been triggered for this alert
 * @param labels            raw Prometheus labels
 * @param annotations       raw Prometheus annotations
 *
 * @since 0.0.1
 */
public record AlertDetailResponse(
    String alertId,
    String alertName,
    String status,
    String severity,
    String namespace,
    String podName,
    String containerName,
    Instant timestamp,
    boolean hasDiagnostics,
    Map<String, String> labels,
    Map<String, String> annotations
) {

    /**
     * Maps a domain {@link Alert} to an {@code AlertDetailResponse}.
     *
     * @param alert the domain alert
     * @return the response DTO
     */
    public static AlertDetailResponse from(Alert alert) {
        return new AlertDetailResponse(
            alert.getAlertId(),
            alert.getAlertName(),
            alert.getStatus() != null ? alert.getStatus().getValue() : null,
            alert.getSeverity() != null ? alert.getSeverity().getValue() : null,
            alert.getNamespace(),
            alert.getPodName(),
            alert.getContainerName(),
            alert.getTimestamp(),
            alert.hasDiagnostics(),
            alert.getLabels(),
            alert.getAnnotations()
        );
    }
}
