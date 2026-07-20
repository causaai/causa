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

    // Global messages
    public static final String UNEXPECTED_ERROR = "Unexpected error occurred";

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

    /**
     * Alert ingestion log messages.
     * Health check log messages.
     *
     * @since 0.0.1
     */
    public static final class HealthCheck {
        private HealthCheck() {}

        public static final String ENDPOINT_CALLED = "Health check endpoint called";
        public static final String ENDPOINT_RESPONSE_PREPARED = "Health check response prepared";
        public static final String ENDPOINT_FAILED = "Health check endpoint failed";
        public static final String SYSTEM_CHECK_STARTED = "System health check started";
        public static final String SYSTEM_CHECK_COMPLETED = "System health check completed";
        public static final String DB_CHECK_PASSED = "Database health check passed";
        public static final String DB_CHECK_FAILED = "Database health check failed";
        public static final String DB_LATENCY_MEASUREMENT_FAILED = "Database latency measurement failed";
        public static final String MCP_K8S_CHECK_STARTED = "MCP Kubernetes health check started";
        public static final String MCP_K8S_CHECK_PASSED = "MCP Kubernetes health check passed";
        public static final String MCP_K8S_CHECK_FAILED = "MCP Kubernetes health check failed";
        public static final String LLM_CHECK_STARTED = "LLM health check started";
        public static final String LLM_CHECK_PASSED = "LLM health check passed";
        public static final String LLM_CHECK_FAILED = "LLM health check failed";
    }     
    
    /* Alert ingestion log messages.
     */
    public static final class Alert {
        private Alert() {}

        public static final String WEBHOOK_RECEIVED = "Alert webhook received";
        public static final String WEBHOOK_PROCESSED = "Alert webhook processed successfully";
        public static final String ALERT_ACCEPTED = "Alert accepted for processing";
        public static final String ALERT_FILTERED_SEVERITY = "Alert filtered by severity";
        public static final String ALERT_FILTERED_NAMESPACE = "Alert filtered by namespace";
        public static final String ALERT_FILTERED_COOLDOWN = "Alert skipped due to cooldown";
        public static final String ALERT_VALIDATION_FAILED = "Alert webhook validation failed";
        public static final String ALERT_PROCESSING_ERROR = "Error processing alert webhook";
        public static final String COOLDOWN_CACHE_CLEANUP = "Cooldown cache cleanup completed";
        public static final String ALERT_PERSISTED = "Alert persisted to database";

        // Exception messages
        public static final String ALERT_PERSIST_FAILED = "Failed to persist alert";
        public static final String ALERT_UPDATE_FAILED = "Failed to update alert";
        public static final String ALERT_NOT_FOUND = "Alert not found";
    }

    /**
     * Diagnostic pipeline log messages.
     */
    public static final class Diagnostic {
        private Diagnostic() {}

        public static final String DIAGNOSTIC_TRIGGERED = "Diagnostic pipeline triggered";
        public static final String CONTEXT_COLLECTION_STARTED = "Context collection started";
        public static final String DIAGNOSIS_TYPE_DETERMINED = "Diagnosis type determined";
        public static final String ROOT_CAUSE_ANALYSIS_STARTED = "Root cause analysis started";
        public static final String RCA_VALIDATION_STARTED = "RCA validation started";
        public static final String DIAGNOSTIC_COMPLETED = "Diagnostic completed";
        public static final String DIAGNOSTIC_FAILED = "Diagnostic failed";

        // Exception messages
        public static final String DIAGNOSTIC_PERSIST_FAILED = "Failed to persist diagnostic";
        public static final String DIAGNOSTIC_UPDATE_FAILED = "Failed to update diagnostic";
    }

    /**
     * MCP integration log messages.
     */
    public static final class Mcp {
        private Mcp() {}

        public static final String MCP_CONTEXT_COLLECTION_START = "MCP context collection started";
        public static final String MCP_K8S_POD_STATUS = "Kubernetes pod status retrieved";
        public static final String MCP_K8S_POD_EVENTS = "Kubernetes pod events retrieved";
        public static final String MCP_K8S_POD_LOGS = "Kubernetes pod logs retrieved";
        public static final String MCP_CALL_FAILED = "MCP tool call failed";
        public static final String MCP_SKIPPED_NO_POD = "Skipping Kubernetes MCP calls - no pod name in alert";
    }

    /**
     * Validation pipeline log messages.
     */
    public static final class Validation {
        private Validation() {}

        // YAML Rule Engine
        public static final String YAML_RULES_INITIALIZING = "Initializing YAML-based rule sets";
        public static final String YAML_RULES_LOADING = "Loading YAML rule sets";
        public static final String YAML_RULES_LOADED = "YAML rule sets loaded";
        public static final String YAML_RULES_INITIALIZED = "YAML rule sets initialized";
        public static final String YAML_RULE_RELOADING = "Reloading modified rule set";
        public static final String YAML_RULES_HOT_RELOADED = "Hot-reloaded rule sets";
        public static final String YAML_RULE_LOAD_FAILED = "Failed to check for rule set modifications";

        // Hypothesis Validation
        public static final String HYPOTHESIS_VALIDATION_STARTED = "Validating RCA hypothesis with rule-based approach";
        public static final String HYPOTHESIS_VALIDATION_COMPLETED = "Rule-based hypothesis validation completed";
        public static final String HYPOTHESIS_VALIDATION_FAILED = "Rule-based hypothesis validation failed";
        public static final String HYPOTHESIS_IDENTIFIED = "Hypothesis identified";
        public static final String SIGNALS_EXTRACTED = "Signals extracted from diagnostic context";
        public static final String NO_RULESET_AVAILABLE = "No rule set available for hypothesis";
        public static final String YAML_RULESET_LOADED = "Loaded YAML-based rule set";
        public static final String NO_RULESET_FOUND = "No rule set found for hypothesis - check YAML configuration";

        // Validation API
        public static final String VALIDATION_DETAIL_REQUESTED = "Validation detail request received";
        public static final String VALIDATION_DETAIL_RETRIEVED = "Validation detail retrieved";
        public static final String VALIDATION_REQUEST_MISSING_ID = "Validation request missing diagnosticId parameter";
        public static final String VALIDATION_DATA_UNAVAILABLE = "Validation data not available for diagnostic";
        public static final String VALIDATION_DATA_PARSE_FAILED = "Failed to parse validation data";
        public static final String DIAGNOSTIC_NOT_FOUND = "Diagnostic not found";
    }

    /**
     * Common log field names.
     */
    public static final class Fields {
        private Fields() {}

        // Common
        public static final String DIAGNOSTIC_ID = "diagnosticId";
        public static final String ALERT_ID = "alertId";
        public static final String STATUS = "status";
        public static final String EXCEPTION = "exception";

        // Validation
        public static final String HYPOTHESIS = "hypothesis";
        public static final String ANOMALY_TYPE = "anomalyType";
        public static final String ISSUE_TITLE = "issueTitle";
        public static final String SIGNAL_COUNT = "signalCount";
        public static final String CONFIDENCE = "confidence";
        public static final String SCORE = "score";
        public static final String REQUIRED_PASSED = "requiredPassed";
        public static final String REQUIRED_TOTAL = "requiredTotal";
        public static final String SUPPORTING_MATCHED = "supportingMatched";
        public static final String EXCLUSION_MATCHED = "exclusionMatched";
        public static final String FINAL_STATUS = "finalStatus";

        // YAML Rules
        public static final String HOT_RELOAD_ENABLED = "hotReloadEnabled";
        public static final String TOTAL_RULE_SETS = "totalRuleSets";
        public static final String LOADED_RULE_SETS = "loadedRuleSets";
        public static final String HYPOTHESES = "hypotheses";
        public static final String FILE = "file";
        public static final String COUNT = "count";
        public static final String SOURCE = "source";
        public static final String CLASSPATH_DIR = "classpathDir";
        public static final String EXTERNAL_DIR = "externalDir";
        public static final String EXPECTED_LOCATION = "expectedLocation";
    }
}
