package com.causa.api.validators;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;
import org.apache.commons.lang3.StringUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Alert Webhook Validator
 *
 * <p>Validates incoming Prometheus Alertmanager webhook requests.
 * <p>Returns a list of validation error messages (empty list means valid).
 *
 * <p>For {@code vm} platform, only {@code alertname} is required in labels,
 * and {@code workload_name} is expected in annotations.
 * For {@code cluster} platform, {@code alertname}, {@code container}, and {@code namespace} are required in labels.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertWebhookValidator {

    private final String platform;

    @Inject
    public AlertWebhookValidator(
            @ConfigProperty(name = "causa.cluster.target-cluster-type", defaultValue = "cluster") String platform) {
        this.platform = platform != null ? platform.trim().toLowerCase() : "cluster";
    }

    /**
     * Validates the incoming Alertmanager webhook request.
     *
     * @param request the webhook request to validate
     * @return list of validation error messages (empty if valid)
     */
    public List<String> validate(AlertWebhookRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Request body is null");
            return errors;
        }

        if (request.getAlerts() == null || request.getAlerts().isEmpty()) {
            errors.add("Alerts array is null or empty");
        }

        // Validate Alertmanager version if present
        if (request.getVersion() != null
                && !AlertConstants.Webhook.ALERTMANAGER_VERSION.equals(request.getVersion())) {
            errors.add("Unsupported Alertmanager version: " + request.getVersion()
                + " (expected: " + AlertConstants.Webhook.ALERTMANAGER_VERSION + ")");
        }

        // Validate each alert item
        if (request.getAlerts() != null) {
            for (int i = 0; i < request.getAlerts().size(); i++) {
                validateAlertItem(request.getAlerts().get(i), i, errors);
            }
        }

        return errors;
    }

    /**
     * Validates a single alert item.
     *
     * @param item the alert item
     * @param index the item index (for error reporting)
     * @param errors the list to accumulate errors into
     */
    private void validateAlertItem(AlertWebhookRequest.AlertItem item,
                                          int index,
                                          List<String> errors) {
        if (item == null) {
            errors.add("alerts[" + index + "] is null");
            return;
        }

        if (item.getStatus() == null || item.getStatus().isBlank()) {
            errors.add("alerts[" + index + "].status is required");
        }

        if (item.getLabels() == null || item.getLabels().isEmpty()) {
            errors.add("alerts[" + index + "].labels is required");
            return;
        }

        Map<String, String> annotations = item.getAnnotations() != null ? item.getAnnotations() : Map.of();
        Map<String, String> labels      = item.getLabels();

        // alertname is always required
        if (StringUtils.isBlank(labels.get(AlertConstants.Labels.ALERT_NAME))) {
            errors.add("alerts[" + index + "].labels must contain '"
                + AlertConstants.Labels.ALERT_NAME + "'");
        }

        if ("vm".equals(platform)) {
            // VM platform — workload_name is always set as annotation by PrometheusRule
            if (StringUtils.isBlank(annotations.get(AlertConstants.Labels.WORKLOAD_NAME))) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.WORKLOAD_NAME + "' in annotations");
            }
        } else {
            // Cluster platform — container, namespace, and pod are standard Prometheus labels
            if (StringUtils.isBlank(labels.get(AlertConstants.Labels.CONTAINER))) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.CONTAINER + "' in labels");
            }
            if (StringUtils.isBlank(labels.get(AlertConstants.Labels.NAMESPACE))) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.NAMESPACE + "' in labels");
            }
            if (StringUtils.isBlank(labels.get(AlertConstants.Labels.POD))) {
                errors.add("alerts[" + index + "] must contain non-blank '"
                    + AlertConstants.Labels.POD + "' in labels");
            }
        }
    }
}
