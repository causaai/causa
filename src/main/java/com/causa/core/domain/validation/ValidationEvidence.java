package com.causa.core.domain.validation;

import com.causa.common.constants.ValidationConstants;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents evidence found in diagnostic context that supports or refutes an assertion.
 *
 * <p>ValidationEvidence is extracted from MCP context sources like Kubernetes events, pod logs,
 * Prometheus metrics, Kruize recommendations, and Cryostat analysis.
 *
 * <p>This is the validation pipeline's working model. For the database persistence model,
 * see {@link EvidenceItem}. For the API response model, see DiagnosticDetailResponse.Evidence.
 *
 * @since 0.0.1
 */
public record ValidationEvidence(
    String id,
    String source,
    EvidenceType type,
    String snippet,
    String statement,
    String explanation,
    double relevanceScore,
    Optional<String> structuredData
) {

    /**
     * Creates a new validation evidence instance.
     *
     * @param id unique identifier for this evidence (for ranking/deduplication)
     * @param source the data source (e.g., "kubernetes-events", "prometheus-metrics")
     * @param type the type of evidence
     * @param snippet the relevant text/data snippet
     * @param statement one line stating only what the snippet shows; null for rule-derived
     *                  evidence, which has no narrator to write one
     * @param explanation why this snippet bears on the assertion - what it proves or rules out;
     *                    null for the same reason
     * @param relevanceScore how relevant this evidence is (0.0 to 1.0)
     * @param structuredData optional structured representation (JSON)
     */
    public ValidationEvidence {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ValidationEvidence ID cannot be null or blank");
        }
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
     * Creates simple evidence without structured data (with explicit ID).
     */
    public static ValidationEvidence of(String id, String source, EvidenceType type, String snippet, double relevanceScore) {
        return new ValidationEvidence(id, source, type, snippet, null, null, relevanceScore, Optional.empty());
    }

    /**
     * Creates evidence with structured data (with explicit ID).
     */
    public static ValidationEvidence of(String id, String source, EvidenceType type, String snippet, double relevanceScore, String structuredData) {
        return new ValidationEvidence(id, source, type, snippet, null, null, relevanceScore, Optional.of(structuredData));
    }

    /**
     * Creates narrated evidence (with explicit ID).
     */
    public static ValidationEvidence of(String id, String source, EvidenceType type, String snippet, String statement,
                              String explanation, double relevanceScore) {
        return new ValidationEvidence(id, source, type, snippet, statement, explanation, relevanceScore, Optional.empty());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Backward-compatible factory methods (auto-generate ID)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates simple evidence without structured data (auto-generates ID).
     * @deprecated Use {@link #of(String, String, EvidenceType, String, double)} with explicit ID
     */
    @Deprecated(since = "0.0.2")
    public static ValidationEvidence of(String source, EvidenceType type, String snippet, double relevanceScore) {
        return new ValidationEvidence(generateId(source, type, snippet), source, type, snippet, null, null, relevanceScore, Optional.empty());
    }

    /**
     * Creates evidence with structured data (auto-generates ID).
     * @deprecated Use {@link #of(String, String, EvidenceType, String, double, String)} with explicit ID
     */
    @Deprecated(since = "0.0.2")
    public static ValidationEvidence of(String source, EvidenceType type, String snippet, double relevanceScore, String structuredData) {
        return new ValidationEvidence(generateId(source, type, snippet), source, type, snippet, null, null, relevanceScore, Optional.of(structuredData));
    }

    /**
     * Creates narrated evidence (auto-generates ID).
     * @deprecated Use {@link #of(String, String, EvidenceType, String, String, String, double)} with explicit ID
     */
    @Deprecated(since = "0.0.2")
    public static ValidationEvidence of(String source, EvidenceType type, String snippet, String statement,
                              String explanation, double relevanceScore) {
        return new ValidationEvidence(generateId(source, type, snippet), source, type, snippet, statement, explanation, relevanceScore, Optional.empty());
    }

    /**
     * Generates a unique UUID-based ID for database primary key.
     */
    private static String generateId(String source, EvidenceType type, String snippet) {
        return UUID.randomUUID().toString();
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
        private String statement;
        private String explanation;
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

        public Builder statement(String statement) {
            this.statement = statement;
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = explanation;
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

        public ValidationEvidence build() {
            String snippet = snippetBuilder.toString();
            return new ValidationEvidence(
                generateId(source, type, snippet),  // Auto-generate ID
                source,
                type,
                snippet,
                statement,
                explanation,
                relevanceScore,
                Optional.ofNullable(structuredData)
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
