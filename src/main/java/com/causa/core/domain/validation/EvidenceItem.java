package com.causa.core.domain.validation;

import java.time.Instant;
import java.util.Map;

/**
 * EvidenceItem - Internal Evidence Model (11 fields).
 *
 * <p>Complete evidence model for storage and debugging. Stores all metadata
 * about evidence collected during validation (PATH A + PATH B).
 *
 * <p>This is the INTERNAL model - stored in DiagnosticEntity.allEvidence as JSON.
 * For the USER-FACING model, see DiagnosticDetailResponse.Evidence (5 fields).
 *
 * @since 0.0.1
 */
public record EvidenceItem(
    String id,
    // Source of evidence (MCP server name or tool identifier). String to support plug-and-play MCP servers.
    // Examples: "kubernetes-mcp", "prometheus-mcp", "kruize-mcp", "cryostat-mcp", "custom-profiler-mcp"
    String source,
    EvidenceType type,
    EvidenceStrength strength,
    EvidenceHypothesisAlignment evidenceHypothesisAlignment,
    String rawSnippet,
    String reasoning,
    double confidence,
    int priority,
    Instant collectedAt,
    Map<String, String> metadata
) {

    /**
     * Type of evidence - what kind of data it is.
     */
    public enum EvidenceType {
        /** Kubernetes event (reason, message) */
        KUBERNETES_EVENT,

        /** Container status (exit code, state) */
        CONTAINER_STATUS,

        /** Pod status (phase, conditions) */
        POD_STATUS,

        /** Metric value (gauge, counter) */
        METRIC,

        /** Time series data */
        TIME_SERIES,

        /** Log pattern match */
        LOG_PATTERN,

        /** Single log entry */
        LOG_ENTRY,

        /** JVM analysis result */
        JVM_ANALYSIS,

        /** GC analysis result */
        GC_ANALYSIS,

        /** Memory analysis result */
        MEMORY_ANALYSIS,

        /** Thread analysis result */
        THREAD_ANALYSIS,

        /** Heap dump analysis result */
        HEAP_DUMP_ANALYSIS,

        /** Recommendation from optimizer */
        RECOMMENDATION,

        /** Optimization suggestion */
        OPTIMIZATION_SUGGESTION,

        /** Other evidence type */
        OTHER
    }

    /**
     * Strength of evidence - how definitive is it?
     */
    public enum EvidenceStrength {
        /** Definitive proof (e.g., exit code 137, OOMKilled status) */
        DEFINITIVE,

        /** Strong indicator (e.g., heap >95%, many Full GCs) */
        STRONG,

        /** Moderate indicator (e.g., heap >80%, increasing trend) */
        MODERATE,

        /** Weak indicator (e.g., single metric spike) */
        WEAK,

        /** Circumstantial evidence (indirect) */
        CIRCUMSTANTIAL
    }

    /**
     * Evidence-Hypothesis Alignment - does this evidence support or refute the hypothesis?
     */
    public enum EvidenceHypothesisAlignment {
        /** Supports the hypothesis */
        SUPPORTS,

        /** Refutes the hypothesis */
        REFUTES,

        /** Neutral - neither supports nor refutes */
        NEUTRAL,

        /** Conflicts with other evidence */
        CONFLICTS
    }

    /**
     * Builder for creating EvidenceItem instances.
     */
    public static class Builder {
        private String id;
        private String source;
        private EvidenceType type;
        private EvidenceStrength strength;
        private EvidenceHypothesisAlignment evidenceHypothesisAlignment;
        private String rawSnippet;
        private String reasoning;
        private double confidence;
        private int priority;
        private Instant collectedAt;
        private Map<String, String> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder type(EvidenceType type) {
            this.type = type;
            return this;
        }

        public Builder strength(EvidenceStrength strength) {
            this.strength = strength;
            return this;
        }

        public Builder evidenceHypothesisAlignment(EvidenceHypothesisAlignment evidenceHypothesisAlignment) {
            this.evidenceHypothesisAlignment = evidenceHypothesisAlignment;
            return this;
        }

        public Builder rawSnippet(String rawSnippet) {
            this.rawSnippet = rawSnippet;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder collectedAt(Instant collectedAt) {
            this.collectedAt = collectedAt;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EvidenceItem build() {
            return new EvidenceItem(
                id,
                source,
                type,
                strength,
                evidenceHypothesisAlignment,
                rawSnippet,
                reasoning,
                confidence,
                priority,
                collectedAt != null ? collectedAt : Instant.now(),
                metadata != null ? metadata : Map.of()
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
