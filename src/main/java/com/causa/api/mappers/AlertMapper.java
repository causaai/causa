package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.core.domain.Alert;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Alert Mapper
 *
 * <p>Maps Prometheus Alertmanager webhook DTOs to domain Alert objects.
 * <p>Handles label extraction, timestamp parsing, and alert ID generation.
 *
 * @since 0.0.1
 */
public final class AlertMapper {

    private AlertMapper() {
        // Prevent instantiation
    }

    /**
     * Maps an Alertmanager webhook request to a list of domain Alert objects.
     *
     * @param request the webhook request
     * @return list of domain Alert objects
     */
    public static List<Alert> toDomainList(AlertWebhookRequest request) {
        if (request == null || request.getAlerts() == null) {
            return List.of();
        }

        return request.getAlerts().stream()
            .map(AlertMapper::toDomain)
            .toList();
    }

    /**
     * Maps a single Alertmanager alert item to a domain Alert.
     *
     * @param item the alert item from the webhook payload
     * @return the domain Alert object
     */
    public static Alert toDomain(AlertWebhookRequest.AlertItem item) {
        Map<String, String> labels = item.getLabels();
        Map<String, String> annotations = item.getAnnotations();

        String alertName = getLabel(labels, AlertConstants.Labels.ALERT_NAME, "unknown");
        String container = getLabel(labels, AlertConstants.Labels.CONTAINER,
                                      AlertConstants.Defaults.UNKNOWN_CONTAINER);
        String pod = getLabel(labels, AlertConstants.Labels.POD, null);
        String namespace = getLabelWithFallback(labels, annotations,
                                                 AlertConstants.Labels.NAMESPACE,
                                                 AlertConstants.Defaults.DEFAULT_NAMESPACE);
        String severityStr = getLabel(labels, AlertConstants.Labels.SEVERITY,
                                       AlertConstants.Defaults.DEFAULT_SEVERITY_FILTER);

        Instant timestamp = parseTimestamp(item.getStartsAt());
        String alertId = Alert.generateAlertId(container, timestamp);

        return Alert.builder()
            .alertId(alertId)
            .timestamp(timestamp)
            .alertName(alertName)
            .severity(AlertSeverity.fromString(severityStr))
            .podName(pod)
            .containerName(container)
            .namespace(namespace)
            .status(AlertStatus.fromString(item.getStatus()))
            .hasDiagnostics(false)
            .build();
    }

    /**
     * Extracts a label value from the labels map with a default fallback.
     *
     * @param labels the labels map
     * @param key the label key
     * @param defaultValue default value if key not found
     * @return the label value or default
     */
    private static String getLabel(Map<String, String> labels, String key, String defaultValue) {
        if (labels == null) {
            return defaultValue;
        }
        return labels.getOrDefault(key, defaultValue);
    }

    /**
     * Extracts a label with fallback to annotations if not found in labels.
     *
     * <p>Some Alertmanager configurations put namespace/pod in annotations rather than labels.
     *
     * @param labels the labels map
     * @param annotations the annotations map
     * @param key the key to search for
     * @param defaultValue default value if not found anywhere
     * @return the value from labels, annotations, or default
     */
    private static String getLabelWithFallback(Map<String, String> labels,
                                                Map<String, String> annotations,
                                                String key,
                                                String defaultValue) {
        String value = getLabel(labels, key, null);
        if (value != null) {
            return value;
        }

        if (annotations != null) {
            return annotations.getOrDefault(key, defaultValue);
        }

        return defaultValue;
    }

    /**
     * Parses an ISO 8601 timestamp from Alertmanager.
     *
     * <p>Falls back to current time if parsing fails.
     *
     * @param isoTimestamp the ISO timestamp string
     * @return the parsed Instant, or current time if parsing fails
     */
    private static Instant parseTimestamp(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.parse(isoTimestamp);
        } catch (Exception e) {
            // Log warning and fallback to current time
            return Instant.now();
        }
    }
}
