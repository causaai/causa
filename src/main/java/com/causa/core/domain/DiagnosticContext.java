package com.causa.core.domain;

import com.causa.common.constants.ContextConstants;

import java.util.Objects;

/**
 * Diagnostic Context
 *
 * <p>Aggregates all diagnostic context collected from multiple MCP servers (Kubernetes, Kruize, Cryostat).
 * This immutable domain model represents the complete observability data gathered for a single alert.
 *
 * <p>The {@link #toString()} method produces LLM-ready formatted text suitable for passing as context
 * to an LLM for root cause analysis.
 *
 * @since 0.0.1
 */
public final class DiagnosticContext {

    public static final String PLATFORM_CLUSTER = "cluster";
    public static final String PLATFORM_VM = "vm";

    // Identity (from Alert)
    private final String platform;
    private final String workloadName;
    private final String podName;
    private final String containerName;
    private final String namespace;

    // Kubernetes MCP context
    private final String podStatus;
    private final String podEvents;
    private final String podLogs;

    // Kruize MCP context
    private final String costRecommendations;
    private final String performanceRecommendations;

    // Cryostat MCP context
    private final String gcAnalysis;
    private final String memoryAnalysis;
    private final String threadAnalysis;
    private final String exceptionAnalysis;
    private final String containerAnalysis;

    // VM — Filesystem MCP
    private final String logDirectoryListing;
    private final String gcLogContent;

    // VM — JMX MCP
    private final String heapStatus;
    private final String gcActivity;
    private final String threadState;
    private final String gcPressureAnalysis;
    private final String memoryLeakIndicators;
    private final String threadContentionAnalysis;
    private final String jvmRuntimeInfo;

    private DiagnosticContext(Builder builder) {
        this.platform = builder.platform;
        this.workloadName = builder.workloadName;
        this.podName = builder.podName;
        this.containerName = builder.containerName;
        this.namespace = builder.namespace;
        this.podStatus = builder.podStatus;
        this.podEvents = builder.podEvents;
        this.podLogs = builder.podLogs;
        this.costRecommendations = builder.costRecommendations;
        this.performanceRecommendations = builder.performanceRecommendations;
        this.gcAnalysis = builder.gcAnalysis;
        this.memoryAnalysis = builder.memoryAnalysis;
        this.threadAnalysis = builder.threadAnalysis;
        this.exceptionAnalysis = builder.exceptionAnalysis;
        this.containerAnalysis = builder.containerAnalysis;
        this.logDirectoryListing = builder.logDirectoryListing;
        this.gcLogContent = builder.gcLogContent;
        this.heapStatus = builder.heapStatus;
        this.gcActivity = builder.gcActivity;
        this.threadState = builder.threadState;
        this.gcPressureAnalysis = builder.gcPressureAnalysis;
        this.memoryLeakIndicators = builder.memoryLeakIndicators;
        this.threadContentionAnalysis = builder.threadContentionAnalysis;
        this.jvmRuntimeInfo = builder.jvmRuntimeInfo;
    }

    // Getters

    public String getPlatform() {
        return platform;
    }

    public String getWorkloadName() {
        return workloadName;
    }

    public String getPodName() {
        return podName;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPodStatus() {
        return podStatus;
    }

    public String getPodEvents() {
        return podEvents;
    }

    public String getPodLogs() {
        return podLogs;
    }

    public String getCostRecommendations() {
        return costRecommendations;
    }

    public String getPerformanceRecommendations() {
        return performanceRecommendations;
    }

    public String getGcAnalysis() {
        return gcAnalysis;
    }

    public String getMemoryAnalysis() {
        return memoryAnalysis;
    }

    public String getThreadAnalysis() {
        return threadAnalysis;
    }

    public String getExceptionAnalysis() {
        return exceptionAnalysis;
    }

    public String getContainerAnalysis() {
        return containerAnalysis;
    }

    public String getLogDirectoryListing() {
        return logDirectoryListing;
    }

    public String getGcLogContent() {
        return gcLogContent;
    }

    public String getHeapStatus() {
        return heapStatus;
    }

    public String getGcActivity() {
        return gcActivity;
    }

    public String getThreadState() {
        return threadState;
    }

    public String getGcPressureAnalysis() {
        return gcPressureAnalysis;
    }

    public String getMemoryLeakIndicators() {
        return memoryLeakIndicators;
    }

    public String getThreadContentionAnalysis() {
        return threadContentionAnalysis;
    }

    public String getJvmRuntimeInfo() {
        return jvmRuntimeInfo;
    }

    /**
     * Checks if any Kubernetes context was collected.
     *
     * @return true if pod status, events, or logs are present
     */
    public boolean hasKubernetesContext() {
        return isNotBlank(podStatus) || isNotBlank(podEvents) || isNotBlank(podLogs);
    }

    /**
     * Checks if any Kruize context was collected.
     *
     * @return true if cost or performance recommendations are present
     */
    public boolean hasKruizeContext() {
        return isNotBlank(costRecommendations) || isNotBlank(performanceRecommendations);
    }

    /**
     * Checks if any Cryostat context was collected.
     *
     * @return true if any JFR analysis is present
     */
    public boolean hasCryostatContext() {
        return isNotBlank(gcAnalysis)
            || isNotBlank(memoryAnalysis)
            || isNotBlank(threadAnalysis)
            || isNotBlank(exceptionAnalysis)
            || isNotBlank(containerAnalysis);
    }

    /**
     * Checks if any Filesystem MCP context was collected.
     *
     * @return true if directory listing or GC log content is present
     */
    public boolean hasFilesystemContext() {
        return isNotBlank(logDirectoryListing) || isNotBlank(gcLogContent);
    }

    /**
     * Checks if any JMX MCP context was collected.
     *
     * @return true if any JMX metric is present
     */
    public boolean hasJmxContext() {
        return isNotBlank(heapStatus)
            || isNotBlank(gcActivity)
            || isNotBlank(threadState)
            || isNotBlank(gcPressureAnalysis)
            || isNotBlank(memoryLeakIndicators)
            || isNotBlank(threadContentionAnalysis)
            || isNotBlank(jvmRuntimeInfo);
    }

    /**
     * Checks if any diagnostic context was collected.
     *
     * @return true if any context field is non-null and non-blank
     */
    public boolean hasAnyContext() {
        return hasKubernetesContext() || hasKruizeContext() || hasCryostatContext()
            || hasFilesystemContext() || hasJmxContext();
    }

    /**
     * Produces LLM-ready formatted text containing all collected diagnostic context.
     *
     * <p>Each section is labeled with its data source (e.g., "GC ANALYSIS (Cryostat JFR)")
     * so the LLM can understand provenance. Missing sections are explicitly marked as
     * "Not available" rather than omitted, preventing hallucination.
     *
     * @return structured text suitable for LLM context
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(ContextConstants.HEADER).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_PLATFORM).append(ContextConstants.FIELD_SEPARATOR)
            .append(platform != null ? platform : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_WORKLOAD).append(ContextConstants.FIELD_SEPARATOR)
            .append(workloadName != null ? workloadName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);

        if (PLATFORM_VM.equals(platform)) {
            sb.append(ContextConstants.NEWLINE);
            appendVmSections(sb);
        } else {
            // Pod details (cluster only)
            sb.append(ContextConstants.LABEL_POD).append(ContextConstants.FIELD_SEPARATOR)
                .append(podName != null ? podName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
            sb.append(ContextConstants.LABEL_CONTAINER).append(ContextConstants.FIELD_SEPARATOR)
                .append(containerName != null ? containerName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
            sb.append(ContextConstants.LABEL_NAMESPACE).append(ContextConstants.FIELD_SEPARATOR)
                .append(namespace != null ? namespace : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE).append(ContextConstants.NEWLINE);
            appendClusterSections(sb);
        }

        return sb.toString();
    }

    private void appendClusterSections(StringBuilder sb) {
        // Kubernetes context
        appendSection(sb, ContextConstants.SECTION_POD_STATUS, podStatus);
        appendSection(sb, ContextConstants.SECTION_POD_EVENTS, podEvents);
        appendSection(sb, ContextConstants.SECTION_POD_LOGS, podLogs);

        // Kruize context
        appendSection(sb, ContextConstants.SECTION_COST_RECOMMENDATIONS, costRecommendations);
        appendSection(sb, ContextConstants.SECTION_PERF_RECOMMENDATIONS, performanceRecommendations);

        // Cryostat context
        appendSection(sb, ContextConstants.SECTION_GC_ANALYSIS, gcAnalysis);
        appendSection(sb, ContextConstants.SECTION_MEMORY_ANALYSIS, memoryAnalysis);
        appendSection(sb, ContextConstants.SECTION_THREAD_ANALYSIS, threadAnalysis);
        appendSection(sb, ContextConstants.SECTION_EXCEPTION_ANALYSIS, exceptionAnalysis);
        appendSection(sb, ContextConstants.SECTION_CONTAINER_ANALYSIS, containerAnalysis);
    }

    private void appendVmSections(StringBuilder sb) {
        // Filesystem MCP context
        appendSection(sb, ContextConstants.SECTION_VM_LOG_DIR_LISTING,   logDirectoryListing);
        appendSection(sb, ContextConstants.SECTION_VM_GC_LOG_CONTENT,    gcLogContent);

        // JMX MCP context
        appendSection(sb, ContextConstants.SECTION_VM_HEAP_STATUS,       heapStatus);
        appendSection(sb, ContextConstants.SECTION_VM_GC_ACTIVITY,       gcActivity);
        appendSection(sb, ContextConstants.SECTION_VM_THREAD_STATE,      threadState);
        appendSection(sb, ContextConstants.SECTION_VM_GC_PRESSURE,       gcPressureAnalysis);
        appendSection(sb, ContextConstants.SECTION_VM_MEMORY_LEAK,       memoryLeakIndicators);
        appendSection(sb, ContextConstants.SECTION_VM_THREAD_CONTENTION, threadContentionAnalysis);
        appendSection(sb, ContextConstants.SECTION_VM_JVM_RUNTIME_INFO,  jvmRuntimeInfo);
    }

    /**
     * Appends a labeled section to the output.
     *
     * @param sb the string builder
     * @param header the section header
     * @param content the section content (nullable)
     */
    private void appendSection(StringBuilder sb, String header, String content) {
        sb.append(ContextConstants.SECTION_PREFIX).append(header).append(ContextConstants.SECTION_SUFFIX).append(ContextConstants.NEWLINE);
        if (isNotBlank(content)) {
            sb.append(content).append(ContextConstants.NEWLINE);
        } else {
            sb.append(ContextConstants.NOT_AVAILABLE).append(ContextConstants.NEWLINE);
        }
        sb.append(ContextConstants.NEWLINE);
    }

    /**
     * Null-safe blank check.
     */
    private boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * Creates a new builder for constructing DiagnosticContext instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing immutable DiagnosticContext instances.
     *
     * <p>All context fields are nullable since null represents failed/skipped collection.
     */
    public static final class Builder {
        private String platform;
        private String workloadName;
        private String podName;
        private String containerName;
        private String namespace;
        private String podStatus;
        private String podEvents;
        private String podLogs;
        private String costRecommendations;
        private String performanceRecommendations;
        private String gcAnalysis;
        private String memoryAnalysis;
        private String threadAnalysis;
        private String exceptionAnalysis;
        private String containerAnalysis;
        private String logDirectoryListing;
        private String gcLogContent;
        private String heapStatus;
        private String gcActivity;
        private String threadState;
        private String gcPressureAnalysis;
        private String memoryLeakIndicators;
        private String threadContentionAnalysis;
        private String jvmRuntimeInfo;

        private Builder() {}

        public Builder platform(String platform) {
            this.platform = platform;
            return this;
        }

        public Builder workloadName(String workloadName) {
            this.workloadName = workloadName;
            return this;
        }

        public Builder podName(String podName) {
            this.podName = podName;
            return this;
        }

        public Builder containerName(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder podStatus(String podStatus) {
            this.podStatus = podStatus;
            return this;
        }

        public Builder podEvents(String podEvents) {
            this.podEvents = podEvents;
            return this;
        }

        public Builder podLogs(String podLogs) {
            this.podLogs = podLogs;
            return this;
        }

        public Builder costRecommendations(String costRecommendations) {
            this.costRecommendations = costRecommendations;
            return this;
        }

        public Builder performanceRecommendations(String performanceRecommendations) {
            this.performanceRecommendations = performanceRecommendations;
            return this;
        }

        public Builder gcAnalysis(String gcAnalysis) {
            this.gcAnalysis = gcAnalysis;
            return this;
        }

        public Builder memoryAnalysis(String memoryAnalysis) {
            this.memoryAnalysis = memoryAnalysis;
            return this;
        }

        public Builder threadAnalysis(String threadAnalysis) {
            this.threadAnalysis = threadAnalysis;
            return this;
        }

        public Builder exceptionAnalysis(String exceptionAnalysis) {
            this.exceptionAnalysis = exceptionAnalysis;
            return this;
        }

        public Builder containerAnalysis(String containerAnalysis) {
            this.containerAnalysis = containerAnalysis;
            return this;
        }

        // VM — Filesystem MCP

        public Builder logDirectoryListing(String logDirectoryListing) {
            this.logDirectoryListing = logDirectoryListing;
            return this;
        }

        public Builder gcLogContent(String gcLogContent) {
            this.gcLogContent = gcLogContent;
            return this;
        }

        // VM — JMX MCP

        public Builder heapStatus(String heapStatus) {
            this.heapStatus = heapStatus;
            return this;
        }

        public Builder gcActivity(String gcActivity) {
            this.gcActivity = gcActivity;
            return this;
        }

        public Builder threadState(String threadState) {
            this.threadState = threadState;
            return this;
        }

        public Builder gcPressureAnalysis(String gcPressureAnalysis) {
            this.gcPressureAnalysis = gcPressureAnalysis;
            return this;
        }

        public Builder memoryLeakIndicators(String memoryLeakIndicators) {
            this.memoryLeakIndicators = memoryLeakIndicators;
            return this;
        }

        public Builder threadContentionAnalysis(String threadContentionAnalysis) {
            this.threadContentionAnalysis = threadContentionAnalysis;
            return this;
        }

        public Builder jvmRuntimeInfo(String jvmRuntimeInfo) {
            this.jvmRuntimeInfo = jvmRuntimeInfo;
            return this;
        }

        /**
         * Builds the DiagnosticContext instance.
         *
         * @return the constructed DiagnosticContext
         */
        public DiagnosticContext build() {
            return new DiagnosticContext(this);
        }
    }
}
