package com.causa.core.domain.validation;

import com.causa.common.constants.ValidationConstants;

import java.util.List;
import java.util.Optional;

/**
 * Represents evidence found in diagnostic context that supports or refutes an assertion.
 *
 * <p>Evidence is extracted from MCP context sources like Kubernetes events, pod logs,
 * Prometheus metrics, Kruize recommendations, and Cryostat analysis.
 *
 * @since 0.0.1
 */
public record Evidence(
    String source,
    EvidenceType type,
    String snippet,
    double relevanceScore,
    Optional<String> structuredData
) {

    /**
     * Creates a new evidence instance.
     *
     * @param source the data source (e.g., "kubernetes-events", "prometheus-metrics")
     * @param type the type of evidence
     * @param snippet the relevant text/data snippet
     * @param relevanceScore how relevant this evidence is (0.0 to 1.0)
     * @param structuredData optional structured representation (JSON)
     */
    public Evidence {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.EVIDENCE_SOURCE_BLANK);
        }
        if (type == null) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.EVIDENCE_TYPE_NULL);
        }
        if (snippet == null || snippet.isBlank()) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.EVIDENCE_SNIPPET_BLANK);
        }
        if (relevanceScore < ValidationConstants.Thresholds.MIN_CONFIDENCE ||
            relevanceScore > ValidationConstants.Thresholds.MAX_CONFIDENCE) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.EVIDENCE_RELEVANCE_RANGE);
        }
        if (structuredData == null) {
            structuredData = Optional.empty();
        }
    }

    /**
     * Creates simple evidence without structured data.
     */
    public static Evidence of(String source, EvidenceType type, String snippet, double relevanceScore) {
        return new Evidence(source, type, snippet, relevanceScore, Optional.empty());
    }

    /**
     * Creates evidence with structured data.
     */
    public static Evidence of(String source, EvidenceType type, String snippet, double relevanceScore, String structuredData) {
        return new Evidence(source, type, snippet, relevanceScore, Optional.of(structuredData));
    }

    /**
     * Type of evidence based on the data source.
     */
    public enum EvidenceType {
        /** Kubernetes pod status or events */
        KUBERNETES_EVENT,

        /** Pod log entry */
        POD_LOG,

        /** Prometheus metric value or trend */
        METRIC,

        /** Kruize recommendation */
        KRUIZE_RECOMMENDATION,

        /** Cryostat JFR analysis */
        CRYOSTAT_ANALYSIS,

        /** GC log analysis */
        GC_ANALYSIS,

        /** Memory analysis */
        MEMORY_ANALYSIS,

        /** Thread dump analysis */
        THREAD_ANALYSIS,

        /** Exception analysis */
        EXCEPTION_ANALYSIS,

        /** Other source */
        OTHER
    }

    /**
     * Builder for creating evidence with multiple pieces.
     */
    public static class Builder {
        private String source;
        private EvidenceType type;
        private StringBuilder snippetBuilder = new StringBuilder();
        private double relevanceScore;
        private String structuredData;

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder type(EvidenceType type) {
            this.type = type;
            return this;
        }

        public Builder snippet(String snippet) {
            this.snippetBuilder.append(snippet);
            return this;
        }

        public Builder addSnippet(String snippet) {
            if (this.snippetBuilder.length() > 0) {
                this.snippetBuilder.append("\n");
            }
            this.snippetBuilder.append(snippet);
            return this;
        }

        public Builder relevanceScore(double score) {
            this.relevanceScore = score;
            return this;
        }

        public Builder structuredData(String data) {
            this.structuredData = data;
            return this;
        }

        public Evidence build() {
            return new Evidence(
                source,
                type,
                snippetBuilder.toString(),
                relevanceScore,
                Optional.ofNullable(structuredData)
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
