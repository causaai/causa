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
        
        // BOB Shell specific log messages
        public static final String BOB_VERSION_CHECK_TIMEOUT = "BOB Shell version check timed out";
        public static final String BOB_AVAILABILITY_CHECK_FAILED = "BOB Shell availability check failed";
        public static final String BOB_SHELL_AVAILABLE = "BOB Shell is available and ready";
        public static final String BOB_SHELL_NOT_AVAILABLE = "BOB Shell is not available";
        public static final String BOB_SHELL_FAILED = "BOB Shell failed";
        public static final String BOB_OUTPUT_MARKERS_NOT_FOUND = "Could not find ---output--- markers in BOB Shell response";
        public static final String BOB_EXTRACTED_TOKEN_USAGE = "Extracted token usage from BOB Shell";
        public static final String BOB_STATS_FIELD_NOT_FOUND = "Stats field not found in BOB Shell output";
        public static final String BOB_STATS_BLOCK_NOT_FOUND = "Could not find statistics block in BOB Shell output";
        public static final String BOB_TOKEN_PARSE_FAILED = "Failed to parse token usage from BOB Shell output";
        
        // BOB Shell error messages
        public static final String BOB_NOT_AVAILABLE = "BOB Shell is not available";
        public static final String BOB_TIMEOUT_TEMPLATE = "BOB Shell execution timed out after %d seconds";
        public static final String BOB_EXIT_CODE_TEMPLATE = "BOB Shell failed with exit code %d";
        public static final String BOB_EMPTY_RESPONSE = "BOB Shell returned empty response";
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
        public static final String MCP_KRUIZE_CHECK_STARTED = "MCP Kruize health check started";
        public static final String MCP_KRUIZE_CHECK_PASSED = "MCP Kruize health check passed";
        public static final String MCP_KRUIZE_CHECK_FAILED = "MCP Kruize health check failed";
        public static final String MCP_CRYOSTAT_CHECK_STARTED = "MCP Cryostat health check started";
        public static final String MCP_CRYOSTAT_CHECK_PASSED = "MCP Cryostat health check passed";
        public static final String MCP_CRYOSTAT_CHECK_FAILED = "MCP Cryostat health check failed";
        public static final String LLM_CHECK_STARTED = "LLM health check started";
        public static final String LLM_CHECK_PASSED = "LLM health check passed";
        public static final String LLM_CHECK_FAILED = "LLM health check failed";
    }     
    
    /**
     * Alert ingestion log messages.
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

        // Alerts API
        public static final String ALERTS_GET_REQUEST   = "GET /api/v1/alerts request received";
        public static final String ALERTS_GET_FOUND     = "Alert(s) retrieved successfully";
        public static final String ALERTS_GET_NOT_FOUND = "Alert not found";

        // Exception messages
        public static final String ALERT_PERSIST_FAILED = "Failed to persist alert";
        public static final String ALERT_UPDATE_FAILED  = "Failed to update alert";
        public static final String ALERT_NOT_FOUND      = "Alert not found";
    }

    /**
     * Diagnostic pipeline log messages.
     */
    public static final class Diagnostic {
        private Diagnostic() {}

        // Lifecycle
        public static final String DIAGNOSTIC_TRIGGERED       = "Diagnostic pipeline triggered";
        public static final String DIAGNOSTIC_INITIATED       = "Diagnostic initiated — PENDING saved, pipeline dispatched async";
        public static final String DIAGNOSTIC_PIPELINE_START  = "Async diagnostic pipeline started";
        public static final String DIAGNOSTIC_PIPELINE_DONE   = "Async diagnostic pipeline completed";
        public static final String DIAGNOSTIC_PIPELINE_FAILED = "Async diagnostic pipeline failed";
        public static final String DIAGNOSTIC_COMPLETED       = "Diagnostic completed";
        public static final String DIAGNOSTIC_FAILED          = "Diagnostic failed";

        // Context collection
        public static final String CONTEXT_COLLECTION_STARTED = "Context collection started";
        public static final String CONTEXT_COLLECTED          = "Diagnostic context collected — LLM-ready format";

        // RCA
        public static final String ROOT_CAUSE_ANALYSIS_STARTED = "Root cause analysis started";
        public static final String RCA_PROMPT_BUILT             = "RCA prompt built";
        public static final String LLM_RESPONSE_RECEIVED        = "LLM response received";
        public static final String RCA_GENERATED_SUCCESS        = "RCA generated successfully";
        public static final String RCA_GENERATION_FAILED        = "RCA generation failed";

        // Exception messages
        public static final String DIAGNOSTIC_PERSIST_FAILED = "Failed to persist diagnostic";
        public static final String DIAGNOSTIC_UPDATE_FAILED  = "Failed to update diagnostic";

        // Diagnostics query API
        public static final String DIAGNOSTICS_LIST_REQUEST  = "GET /api/v1/diagnostics request received";
        public static final String DIAGNOSTICS_LIST_RETURNED = "Diagnostics list returned";
        public static final String DIAGNOSTIC_GET_REQUEST    = "GET /api/v1/diagnostics/{id} request received";
        public static final String DIAGNOSTIC_GET_FOUND      = "Diagnostic retrieved successfully";
        public static final String DIAGNOSTIC_GET_NOT_FOUND  = "Diagnostic not found";
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
        public static final String MCP_ERROR_DETECTED = "MCP response contains error, returning No Data Available";
        public static final String MCP_SKIPPED_NO_POD = "Skipping Kubernetes MCP calls - no pod name in alert";

        // Kruize MCP
        public static final String MCP_KRUIZE_COST_RECOMMENDATIONS = "Kruize cost recommendations retrieved";
        public static final String MCP_KRUIZE_PERF_RECOMMENDATIONS = "Kruize performance recommendations retrieved";
        public static final String MCP_KRUIZE_SKIPPED_NO_CONTAINER = "Skipping Kruize MCP calls - no container name available";

        // Cryostat MCP
        public static final String MCP_CRYOSTAT_GC_ANALYSIS = "Cryostat GC analysis retrieved";
        public static final String MCP_CRYOSTAT_MEMORY_ANALYSIS = "Cryostat memory analysis retrieved";
        public static final String MCP_CRYOSTAT_THREAD_ANALYSIS = "Cryostat thread analysis retrieved";
        public static final String MCP_CRYOSTAT_EXCEPTION_ANALYSIS = "Cryostat exception analysis retrieved";
        public static final String MCP_CRYOSTAT_CONTAINER_ANALYSIS = "Cryostat container analysis retrieved";
        public static final String MCP_CRYOSTAT_RECORDING_CREATED = "Cryostat recording created, waiting for retry";
        public static final String MCP_CRYOSTAT_RETRY = "Retrying Cryostat tool call";
        public static final String MCP_CRYOSTAT_MAX_RETRIES = "Cryostat max retries exceeded";

        // Context collection completion
        public static final String MCP_CONTEXT_COLLECTION_COMPLETE = "MCP context collection completed";

        // Platform routing
        public static final String MCP_PLATFORM_DETECTED = "Deployment platform detected";
        public static final String MCP_VM_CONTEXT_COLLECTION_START = "VM platform MCP context collection started";
        public static final String MCP_VM_CONTEXT_COLLECTION_COMPLETE = "VM platform MCP context collection completed";

        // Filesystem MCP
        public static final String MCP_FILESYSTEM_DIR_LISTING  = "Filesystem directory listing retrieved";
        public static final String MCP_FILESYSTEM_FILE_CONTENT = "Filesystem file content retrieved";
        public static final String MCP_FILESYSTEM_SKIPPED      = "Skipping Filesystem MCP calls - not yet implemented";

        // JMX MCP
        public static final String MCP_JMX_HEAP_STATUS             = "JMX heap status retrieved";
        public static final String MCP_JMX_GC_ACTIVITY             = "JMX GC activity retrieved";
        public static final String MCP_JMX_THREAD_STATE            = "JMX thread state retrieved";
        public static final String MCP_JMX_GC_PRESSURE             = "JMX GC pressure analysis retrieved";
        public static final String MCP_JMX_MEMORY_LEAK_INDICATORS  = "JMX memory leak indicators retrieved";
        public static final String MCP_JMX_THREAD_CONTENTION       = "JMX thread contention analysis retrieved";
        public static final String MCP_JMX_JVM_RUNTIME_INFO        = "JMX JVM runtime info retrieved";

        // Filesystem MCP (Liberty logs)
        public static final String MCP_FILESYSTEM_LIST_DIRECTORY = "Filesystem MCP list_directory_with_sizes called for Liberty logs";
        public static final String MCP_FILESYSTEM_READ_FILE = "Filesystem MCP read_text_file called for Liberty log file";
        public static final String MCP_FILESYSTEM_LIBERTY_LOGS_COLLECTED = "Liberty log files collected via Filesystem MCP";
        public static final String MCP_FILESYSTEM_SKIPPED_NO_POD = "Skipping Filesystem MCP calls - no pod name in alert";
        public static final String MCP_FILESYSTEM_FILE_SKIPPED_SIZE = "Skipping Liberty log file — exceeds size threshold";
        public static final String MCP_FILESYSTEM_FILE_SKIPPED_WINDOW = "Skipping Liberty log file — outside alert time window";
        public static final String MCP_FILESYSTEM_FFDC_LIST = "Filesystem MCP list_directory_with_sizes called for Liberty FFDC directory";
    }
}
