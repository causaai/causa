package com.causa.api.validators;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Path Parameter Validator
 *
 * <p>Validates path parameters for REST endpoints.
 * <p>Returns a list of validation error messages (empty list means valid).
 *
 * @since 0.0.1
 */
public final class PathParamValidator {

    private PathParamValidator() {
        // Prevent instantiation
    }

    private static final Pattern ALERT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+-\\d+$");
    private static final Pattern DIAGNOSTIC_ID_PATTERN = Pattern.compile("^diag-[a-zA-Z0-9_-]+-\\d+$");
    private static final Pattern CONTAINER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private static final int MAX_CONTAINER_NAME_LENGTH = 255;
    private static final int MAX_ALERT_ID_LENGTH = 512;
    private static final int MAX_DIAGNOSTIC_ID_LENGTH = 512;

    /**
     * Validates an alert ID path parameter.
     *
     * <p>Expected format: {containerName}-{epochMillis}
     *
     * @param alertId the alert ID to validate
     * @return list of validation error messages (empty if valid)
     */
    public static List<String> validateAlertId(String alertId) {
        List<String> errors = new ArrayList<>();

        if (alertId == null || alertId.isBlank()) {
            errors.add("Alert ID cannot be null or blank");
            return errors;
        }

        if (alertId.length() > MAX_ALERT_ID_LENGTH) {
            errors.add("Alert ID exceeds maximum length of " + MAX_ALERT_ID_LENGTH);
        }

        if (!ALERT_ID_PATTERN.matcher(alertId).matches()) {
            errors.add("Alert ID has invalid format (expected: containerName-epochMillis)");
        }

        return errors;
    }

    /**
     * Validates a diagnostic ID path parameter.
     *
     * <p>Expected format: diag-{alertId}-{epochMillis}
     *
     * @param diagnosticId the diagnostic ID to validate
     * @return list of validation error messages (empty if valid)
     */
    public static List<String> validateDiagnosticId(String diagnosticId) {
        List<String> errors = new ArrayList<>();

        if (diagnosticId == null || diagnosticId.isBlank()) {
            errors.add("Diagnostic ID cannot be null or blank");
            return errors;
        }

        if (diagnosticId.length() > MAX_DIAGNOSTIC_ID_LENGTH) {
            errors.add("Diagnostic ID exceeds maximum length of " + MAX_DIAGNOSTIC_ID_LENGTH);
        }

        if (!DIAGNOSTIC_ID_PATTERN.matcher(diagnosticId).matches()) {
            errors.add("Diagnostic ID has invalid format (expected: diag-alertId-epochMillis)");
        }

        return errors;
    }

    /**
     * Validates a container name path parameter.
     *
     * <p>Allows alphanumeric characters, dots, hyphens, and underscores.
     *
     * @param containerName the container name to validate
     * @return list of validation error messages (empty if valid)
     */
    public static List<String> validateContainerName(String containerName) {
        List<String> errors = new ArrayList<>();

        if (containerName == null || containerName.isBlank()) {
            errors.add("Container name cannot be null or blank");
            return errors;
        }

        if (containerName.length() > MAX_CONTAINER_NAME_LENGTH) {
            errors.add("Container name exceeds maximum length of " + MAX_CONTAINER_NAME_LENGTH);
        }

        if (!CONTAINER_NAME_PATTERN.matcher(containerName).matches()) {
            errors.add("Container name contains invalid characters (allowed: alphanumeric, dot, hyphen, underscore)");
        }

        return errors;
    }
}
