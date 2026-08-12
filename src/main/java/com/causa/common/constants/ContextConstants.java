package com.causa.common.constants;

/**
 * Context Constants
 *
 * <p>Contains display constants for formatting diagnostic context output.
 * These constants are used to produce LLM-ready formatted text for diagnostic analysis.
 *
 * @since 0.0.1
 */
public final class ContextConstants {

    private ContextConstants() {
        // Prevent instantiation
    }

    // Display constants
    public static final String HEADER = "=== DIAGNOSTIC CONTEXT ===";
    public static final String SECTION_PREFIX = "--- ";
    public static final String SECTION_SUFFIX = " ---";
    public static final String NOT_AVAILABLE = "No Data Available";
    public static final String FIELD_SEPARATOR = ": ";
    
    // Logging format constants
    public static final int SEPARATOR_LENGTH = 80;
    public static final String SEPARATOR_CHAR = "=";
    public static final String CONTEXT_LOG_HEADER = "COLLECTED DIAGNOSTIC CONTEXT (LLM-Ready)";
    public static final String NEWLINE = "\n";

    // Section headers
    public static final String SECTION_POD_STATUS = "POD STATUS";
    public static final String SECTION_POD_EVENTS = "POD EVENTS";
    public static final String SECTION_POD_LOGS = "POD LOGS (recent)";
    public static final String SECTION_PREVIOUS_POD_LOGS = "PREVIOUS CONTAINER LOGS (pre-crash)";
    public static final String SECTION_COST_RECOMMENDATIONS = "RESOURCE COST RECOMMENDATIONS (Kruize)";
    public static final String SECTION_PERF_RECOMMENDATIONS = "RESOURCE PERFORMANCE RECOMMENDATIONS (Kruize)";
    public static final String SECTION_GC_ANALYSIS = "GC ANALYSIS (Cryostat JFR)";
    public static final String SECTION_MEMORY_ANALYSIS = "MEMORY ANALYSIS (Cryostat JFR)";
    public static final String SECTION_THREAD_ANALYSIS = "THREAD ANALYSIS (Cryostat JFR)";
    public static final String SECTION_EXCEPTION_ANALYSIS = "EXCEPTION ANALYSIS (Cryostat JFR)";
    public static final String SECTION_CONTAINER_ANALYSIS = "CONTAINER RESOURCE ANALYSIS (Cryostat JFR)";
    public static final String SECTION_LIBERTY_LOGS = "LIBERTY APPLICATION LOGS (Filesystem MCP)";

    // Async Profiler MCP section headers
    public static final String SECTION_JFR_REPORT    = "JFR REPORT (Async Profiler MCP)";
    public static final String SECTION_FLAME_GRAPH   = "FLAME GRAPH (Async Profiler MCP)";

    // Quarkus MCP section headers
    public static final String SECTION_QUARKUS_METRICS = "QUARKUS METRICS SNAPSHOT (Quarkus MCP)";
    public static final String SECTION_QUARKUS_HEALTH  = "QUARKUS HEALTH STATUS (Quarkus MCP)";

    // Identity field labels
    public static final String LABEL_POD = "Pod";
    public static final String LABEL_CONTAINER = "Container";
    public static final String LABEL_NAMESPACE = "Namespace";
    public static final String LABEL_PLATFORM = "Platform";
    public static final String LABEL_WORKLOAD = "Workload";
    public static final String LABEL_NOT_APPLICABLE = "N/A";

    // VM platform section headers
    public static final String SECTION_VM_LOG_DIR_LISTING      = "LOG DIRECTORY LISTING (Filesystem MCP)";
    public static final String SECTION_VM_GC_LOG_CONTENT       = "VERBOSE GC LOG CONTENT (Filesystem MCP)";
    public static final String SECTION_VM_HEAP_STATUS          = "HEAP STATUS (JMX MCP)";
    public static final String SECTION_VM_GC_ACTIVITY          = "GC ACTIVITY (JMX MCP)";
    public static final String SECTION_VM_THREAD_STATE         = "THREAD STATE (JMX MCP)";
    public static final String SECTION_VM_GC_PRESSURE          = "GC PRESSURE ANALYSIS (JMX MCP)";
    public static final String SECTION_VM_MEMORY_LEAK          = "MEMORY LEAK INDICATORS (JMX MCP)";
    public static final String SECTION_VM_THREAD_CONTENTION    = "THREAD CONTENTION ANALYSIS (JMX MCP)";
    public static final String SECTION_VM_JVM_RUNTIME_INFO     = "JVM RUNTIME INFO (JMX MCP)";
}

