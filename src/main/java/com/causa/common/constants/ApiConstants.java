package com.causa.common.constants;

/**
 * API Constants
 *
 * <p>Contains API response keys and health-check constants.
 *
 * @since 0.0.1
 */
public final class ApiConstants {

    private ApiConstants() {
        // Prevent instantiation
    }

    /**
     * API endpoint paths.
     */
    public static final class Endpoints {
        private Endpoints() {}

        public static final String HEALTH = "/api/health";
    }

    /**
     * Common API response keys.
     */
    public static final class Response {
        private Response() {}

        public static final String STATUS_KEY = "status";
        public static final String MESSAGE_KEY = "message";
    }

    /**
     * Common API status values.
     */
    public static final class Status {
        private Status() {}

        public static final String UP = "UP";
        public static final String DOWN = "DOWN";
        public static final String READY = "READY";
        public static final String NOT_READY = "NOT_READY";
    }

    /**
     * Health check constants.
     */
    public static final class Health {
        private Health() {}

        public static final String LIVENESS_NAME = "causa-liveness";
        public static final String READINESS_NAME = "causa-readiness";

        public static final String LIVENESS_UP_MESSAGE = "Causa is alive and running";
        public static final String READINESS_UP_MESSAGE = "Causa is ready to accept requests";
        public static final String READINESS_DOWN_MESSAGE = "Causa is not ready to accept requests";
    }

    /**
     * Health check response field keys.
     */
    public static final class HealthCheckResponse {
        private HealthCheckResponse() {}

        public static final String TIMESTAMP_KEY = "timestamp";
        public static final String VERSION_KEY = "version";
        public static final String COMPONENTS_KEY = "components";
        public static final String LATENCY_MS_KEY = "latency_ms";
    }

    /**
     * Log field keys.
     */
    public static final class LogFields {
        private LogFields() {}

        public static final String STATUS = "status";
        public static final String HTTP_STATUS = "http_status";
        public static final String LATENCY_MS = "latency_ms";
    }
}
