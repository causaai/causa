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

    // Identity (from Alert)
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

    private DiagnosticContext(Builder builder) {
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
    }

    // Getters

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
     * Checks if any diagnostic context was collected.
     *
     * @return true if any context field is non-null and non-blank
     */
    public boolean hasAnyContext() {
        return hasKubernetesContext() || hasKruizeContext() || hasCryostatContext();
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

        // Header and pod details
        sb.append(ContextConstants.HEADER).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_POD).append(ContextConstants.FIELD_SEPARATOR)
            .append(podName != null ? podName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_CONTAINER).append(ContextConstants.FIELD_SEPARATOR)
            .append(containerName != null ? containerName : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE);
        sb.append(ContextConstants.LABEL_NAMESPACE).append(ContextConstants.FIELD_SEPARATOR)
            .append(namespace != null ? namespace : ContextConstants.LABEL_NOT_APPLICABLE).append(ContextConstants.NEWLINE).append(ContextConstants.NEWLINE);

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

        return sb.toString();
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

        private Builder() {}

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
