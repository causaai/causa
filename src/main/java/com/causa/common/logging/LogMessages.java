package com.causa.common.logging;

/**
 * Log Messages Constants
 *
 * <p>Centralized log message templates for consistent logging across the application.
 * <p><strong>NO MAGIC STRINGS POLICY:</strong> All log messages must be defined here.
 *
 *
 * @since 0.0.1
 */
public final class LogMessages {

    private LogMessages() {
        // Prevent instantiation
    }

    public static final class Health {
        private Health() {}

        public static final String LIVENESS_CHECK_CALLED = "Liveness check called";
        public static final String READINESS_CHECK_PASSED = "Readiness check passed";
        public static final String READINESS_CHECK_FAILED = "Readiness check failed";
    }

    /**
     * Database connection and pool log messages.
     *
     * @since 1.0.0
     */
    public static final class Database {
        private Database() {}

        public static final String CONNECTION_VERIFYING = "Verifying database connection on startup";
        public static final String CONNECTION_SUCCESS = "Database connection pool initialized successfully";
        public static final String CONNECTION_FAILED = "Database connection verification failed";
        public static final String READINESS_CHECK_PASSED = "Database readiness check passed";
        public static final String READINESS_CHECK_FAILED = "Database readiness check failed";
    }
}
