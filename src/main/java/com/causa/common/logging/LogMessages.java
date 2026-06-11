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
        public static final String LLM_READINESS_PASSED = "LLM readiness check passed";
        public static final String LLM_READINESS_FAILED = "LLM readiness check failed";
    }

    public static final class LLM {
        private LLM() {}

        // Startup
        public static final String LLM_FACTORY_INITIALIZING = "Initializing LLM chat model factory";
        public static final String LLM_PROVIDER_DETECTED = "LLM provider detected";
        public static final String LLM_READY = "LLM ready";
        public static final String LLM_STARTUP_FAILED = "LLM startup failed";
        public static final String CONNECTIVITY_CHECK_START = "Verifying LLM connectivity";
        public static final String CONNECTIVITY_CHECK_SUCCESS = "LLM connectivity verified";
        public static final String CONNECTIVITY_CHECK_FAILED = "LLM connectivity check failed";

        // Prompt operations
        public static final String PROMPT_SEND_START = "Sending prompt to LLM";
        public static final String PROMPT_SEND_SUCCESS = "Prompt sent successfully";

        // Errors
        public static final String LLM_ERROR = "LLM error occurred";
        public static final String UNSUPPORTED_PROVIDER = "Unsupported LLM provider";
        public static final String MISSING_CONFIGURATION = "Missing required LLM configuration";
        public static final String MODEL_NOT_AVAILABLE = "LLM chat model not available";
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
