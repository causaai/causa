package com.causa.common.constants;

/**
 * Evidence Constants
 *
 * <p>Constants for the evidence collection pipeline — canonical MCP source names,
 * metadata keys, identifier prefixes, and the relevance bands that derive evidence
 * strength and priority. Rule-derived evidence and selection add their own blocks.
 *
 * @since 0.0.1
 */
public final class EvidenceConstants {

    private EvidenceConstants() {
        // Prevent instantiation
    }

    /**
     * Canonical MCP server names.
     *
     * <p>These match the {@code @WithName} keys in {@code McpConfig} and the server names
     * {@code McpResponseFormatter} keys on, so evidence attribution lines up with the
     * server that actually produced the data.
     */
    public static final class Sources {
        private Sources() {}

        public static final String KUBERNETES     = "kubernetes";
        public static final String KRUIZE         = "kruize";
        public static final String CRYOSTAT       = "cryostat";
        public static final String QUARKUS        = "quarkus";
        public static final String ASYNC_PROFILER = "async-profiler";
        public static final String FILESYSTEM     = "filesystem";
        public static final String JMX            = "jmx";

        /** Fallback when a source label cannot be matched to any known section. */
        public static final String UNKNOWN        = "unknown";
    }

    /**
     * Keys used in {@code EvidenceItem.metadata}.
     */
    public static final class Metadata {
        private Metadata() {}

        public static final String PATH                 = "path";
        public static final String PATH_A               = "A";
        public static final String FINDING_ID           = "findingId";
        public static final String ANOMALY_TYPE         = "anomalyType";

        // PATH A
        public static final String ASSERTION_ID         = "assertionId";
        public static final String ASSERTION_TYPE       = "assertionType";
        public static final String ASSERTION_STATUS     = "assertionStatus";
        /** The evidence source string exactly as the LLM emitted it, before normalisation. */
        public static final String RAW_SOURCE_LABEL     = "rawSourceLabel";

    }

    /**
     * Identifier prefixes for generated evidence item IDs.
     */
    public static final class Ids {
        private Ids() {}

        public static final String PATH_A_PREFIX = "pathA.";
        public static final String EVIDENCE_INFIX = ".ev-";
        public static final String EVIDENCE_INDEX_FORMAT = "%02d";
    }

    /**
     * Display priority — lower sorts first when selecting evidence for the UI.
     */
    public static final class Priority {
        private Priority() {}

        /** Definitive facts that directly settle the hypothesis. */
        public static final int PRIMARY      = 1;
        /** Strong corroborating facts. */
        public static final int SECONDARY    = 2;
        /** Supporting context (limits, counts, configuration). */
        public static final int CONTEXTUAL   = 3;
    }

    /**
     * Relevance-score bands used to derive {@code EvidenceStrength} from PATH A evidence.
     */
    public static final class StrengthBands {
        private StrengthBands() {}

        public static final double DEFINITIVE = 0.95;
        public static final double STRONG     = 0.85;
        public static final double MODERATE   = 0.65;
        public static final double WEAK       = 0.40;
    }

    /**
     * Sentinel text and truncation limits for snippets.
     */
    public static final class Snippet {
        private Snippet() {}

        /** Maximum rawSnippet length retained on an EvidenceItem. */
        public static final int MAX_LENGTH = 2000;
        public static final String TRUNCATION_SUFFIX = "…";
    }
}
