package com.causa.common.constants;

/**
 * Evidence Constants
 *
 * <p>Constants for the evidence collection pipeline — the MCP source fallback, identifier
 * prefixes, and the relevance bands that derive evidence strength. Rule-derived evidence
 * adds its own blocks.
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
     * <p>The names themselves are not listed here: evidence is attributed using the server names
     * declared in {@code mcp.json}, read at runtime by {@code McpSourceResolver}. Only the
     * fallback needs a constant, because no config file can supply it.
     */
    public static final class Sources {
        private Sources() {}

        /** Fallback when a source label cannot be matched to any declared section. */
        public static final String UNKNOWN = "unknown";
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
     * Display priority carried by every evidence item until the ranking design is finalised.
     * Lower sorts first, so a flat value leaves ordering to confidence and strength.
     */
    public static final int DEFAULT_PRIORITY = 1;

    /**
     * Relevance-score bands used to derive {@code EvidenceStrength} from PATH A evidence.
     * One band per {@code EvidenceStrength}, each the inclusive floor of its band.
     */
    public static final class StrengthBands {
        private StrengthBands() {}

        public static final double DEFINITIVE     = 0.95;
        public static final double STRONG         = 0.85;
        public static final double MODERATE       = 0.65;
        public static final double WEAK           = 0.40;
        public static final double CIRCUMSTANTIAL = 0.0;
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
