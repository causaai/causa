package com.causa.api.validators;

import com.causa.api.dto.request.AlertWebhookRequest;
import com.causa.common.constants.AlertConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Alert Webhook Validator
 *
 * <p>Validates incoming Prometheus Alertmanager webhook requests.
 * <p>Returns a list of validation error messages (empty list means valid).
 *
 * @since 0.0.1
 */
public final class AlertWebhookValidator {

    private AlertWebhookValidator() {
        // Prevent instantiation
    }

    /**
     * Validates the incoming Alertmanager webhook request.
     *
     * @param request the webhook request to validate
     * @return list of validation error messages (empty if valid)
     */
    public static List<String> validate(AlertWebhookRequest request) {
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
    private static void validateAlertItem(AlertWebhookRequest.AlertItem item,
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

        // Validate required labels
        if (!item.getLabels().containsKey(AlertConstants.Labels.ALERT_NAME)) {
            errors.add("alerts[" + index + "].labels must contain '"
                + AlertConstants.Labels.ALERT_NAME + "'");
        }

        if (!item.getLabels().containsKey(AlertConstants.Labels.CONTAINER)) {
            errors.add("alerts[" + index + "].labels must contain '"
                + AlertConstants.Labels.CONTAINER + "'");
        }

        if (!item.getLabels().containsKey(AlertConstants.Labels.NAMESPACE)) {
            errors.add("alerts[" + index + "].labels must contain '"
                + AlertConstants.Labels.NAMESPACE + "'");
        }
    }
}
