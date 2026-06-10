package com.causa.api.dto.response;

import java.time.Instant;

/**
 * Alert Details Response DTO
 *
 * <p>Represents a single alert in query responses (GET endpoints).
 *
 * @param alertId unique alert identifier
 * @param timestamp when the alert was triggered
 * @param alertName the Prometheus alert name
 * @param severity alert severity (critical, warning, info)
 * @param status alert status (firing, resolved)
 * @param podName the Kubernetes pod name (nullable)
 * @param containerName the container name
 * @param namespace the Kubernetes namespace
 * @param hasDiagnostics whether diagnostic analysis is available
 * @since 0.0.1
 */
public record AlertDetailsResponse(
    String alertId,
    Instant timestamp,
    String alertName,
    String severity,
    String status,
    String podName,
    String containerName,
    String namespace,
    boolean hasDiagnostics
) {
}
