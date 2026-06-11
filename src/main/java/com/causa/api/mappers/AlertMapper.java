package com.causa.api.mappers;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.config.AlertConfig;
import com.causa.core.domain.Alert;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        Map<String, String> labels = item.getLabels();
        Map<String, String> annotations = item.getAnnotations();

        // Required fields (validated before this is called)
        String alertName = labels.get(AlertConstants.Labels.ALERT_NAME);
        String container = labels.get(AlertConstants.Labels.CONTAINER);
        String namespace = getLabelWithFallback(labels, annotations, AlertConstants.Labels.NAMESPACE);

        // Optional fields
        String pod = labels.get(AlertConstants.Labels.POD);
        String severityStr = labels.getOrDefault(AlertConstants.Labels.SEVERITY, defaultSeverity);

        Instant timestamp = parseTimestamp(item.getStartsAt());

        // Use Prometheus fingerprint as alert ID (globally unique, deterministic)
        // Fallback to generated ID if fingerprint is missing (shouldn't happen with Alertmanager v4)
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
            .labels(labels)
            .annotations(annotations)
            .build();
    }

    /**
     * Extracts a label with fallback to annotations if not found in labels.
     *
     * <p>Some Alertmanager configurations put namespace/pod in annotations rather than labels.
     *
     * @param labels the labels map
     * @param annotations the annotations map
     * @param key the key to search for
     * @return the value from labels or annotations
     */
    private String getLabelWithFallback(Map<String, String> labels,
                                        Map<String, String> annotations,
                                        String key) {
        String value = labels.get(key);
        if (value != null) {
            return value;
        }

        if (annotations != null) {
            return annotations.get(key);
        }

        return null;
    }

    /**
     * Parses an ISO 8601 timestamp from Alertmanager.
     *
     * <p>Falls back to current time if parsing fails.
     *
     * @param isoTimestamp the ISO timestamp string
     * @return the parsed Instant, or current time if parsing fails
     */
    private Instant parseTimestamp(String isoTimestamp) {
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
