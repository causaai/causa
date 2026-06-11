package com.causa.common.constants;

/**
 * Alert Constants
 *
 * <p>Contains constants, enums, and types for alert processing, Prometheus Alertmanager integration, and webhook configuration.
 *
 * @since 0.0.1
 */
public final class AlertConstants {

    private AlertConstants() {
        // Prevent instantiation
    }

    /**
     * Prometheus Alertmanager label keys.
     */
    public static final class Labels {
        private Labels() {}

        public static final String ALERT_NAME = "alertname";
        public static final String SEVERITY = "severity";
        public static final String NAMESPACE = "namespace";
        public static final String POD = "pod";
        public static final String CONTAINER = "container";
    }


    /**
     * Alertmanager webhook constants.
     */
    public static final class Webhook {
        private Webhook() {}

        public static final String ALERTMANAGER_VERSION = "4";
    }

    /**
     * Alert response status values.
     */
    public static final class Response {
        private Response() {}

        public static final String ACCEPTED = "accepted";
        public static final String PARTIAL = "partial";
        public static final String REJECTED = "rejected";
    }

    /**
     * Alert Severity Levels
     *
     * <p>Defines the severity levels for incoming alerts from Prometheus Alertmanager.
     * <p>Ordinal ordering: CRITICAL (0) &gt; WARNING (1) &gt; INFO (2)
     */
    public enum AlertSeverity {

        CRITICAL("critical"),
        WARNING("warning"),
        INFO("info");

        private final String value;

        AlertSeverity(String value) {
            this.value = value;
        }

        /**
         * Returns the string value of this severity.
         *
         * @return the severity value
         */
        public String getValue() {
            return value;
        }

        /**
         * Converts a string to an AlertSeverity enum value (case-insensitive).
         *
         * @param value the string value
         * @return the corresponding AlertSeverity
         * @throws IllegalArgumentException if the value doesn't match any severity
         */
        public static AlertSeverity fromString(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Alert severity value cannot be null or blank");
            }

            String normalized = value.trim().toLowerCase();

            for (AlertSeverity severity : AlertSeverity.values()) {
                if (severity.value.equals(normalized)) {
                    return severity;
                }
            }

            throw new IllegalArgumentException("Unknown alert severity: " + value);
        }

        /**
         * Returns true if this severity is at or above the given minimum severity.
         *
         * <p>Order: CRITICAL &gt; WARNING &gt; INFO
         * <p>Examples:
         * <ul>
         *   <li>CRITICAL.isAtLeast(WARNING) = true</li>
         *   <li>WARNING.isAtLeast(CRITICAL) = false</li>
         *   <li>INFO.isAtLeast(INFO) = true</li>
         * </ul>
         *
         * @param minimum the minimum severity threshold
         * @return true if this severity meets or exceeds the minimum
         */
        public boolean isAtLeast(AlertSeverity minimum) {
            return this.ordinal() <= minimum.ordinal();
        }
    }

    /**
     * Alert Status
     *
     * <p>Defines the status of an alert from Prometheus Alertmanager.
     */
    public enum AlertStatus {

        FIRING("firing"),
        RESOLVED("resolved");

        private final String value;

        AlertStatus(String value) {
            this.value = value;
        }

        /**
         * Returns the string value of this status.
         *
         * @return the status value
         */
        public String getValue() {
            return value;
        }

        /**
         * Converts a string to an AlertStatus enum value (case-insensitive).
         *
         * @param value the string value
         * @return the corresponding AlertStatus
         * @throws IllegalArgumentException if the value doesn't match any status
         */
        public static AlertStatus fromString(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Alert status value cannot be null or blank");
            }

            String normalized = value.trim().toLowerCase();

            for (AlertStatus status : AlertStatus.values()) {
                if (status.value.equals(normalized)) {
                    return status;
                }
            }

            throw new IllegalArgumentException("Unknown alert status: " + value);
        }
    }
}
