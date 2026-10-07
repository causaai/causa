package com.causa.common.constants;

/**
 * Evidence Constants
 *
 * <p>Constants for the evidence collection pipeline — the MCP source fallback, signal
 * provenance keys, identifier prefixes, and the bands that derive evidence strength from
 * an LLM relevance score (PATH A) or a rule weight (PATH B).
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
     * Keys used in {@code Signal.metadata}.
     */
    public static final class Metadata {
        private Metadata() {}

        /**
         * Key under which {@code DiagnosticContextSignalExtractor} stamps the originating
         * context section onto {@code Signal.metadata}. Signals are matched against a
         * flattened context, so without this stamp their provenance is unrecoverable.
         */
        public static final String SIGNAL_SECTION       = "section";

        /**
         * Key under which {@code DiagnosticContextSignalExtractor} stamps the verbatim context
         * line the signal was read from. This is what {@code EvidenceItem.rawSnippet} carries:
         * a reader needs the text the source emitted, not the normalised name/value pair the
         * rules matched on.
         */
        public static final String SIGNAL_SNIPPET       = "snippet";
    }

    /**
     * Reasoning text rendered onto evidence derived from rule evaluation.
     */
    public static final class Messages {
        private Messages() {}

        // The rule description is NOT interpolated into either format: the evaluation reasoning
        // already names what was looked for, and the rule itself is identified by the item id.

        /** {@code <Required|Supporting|Exclusion> rule matched: <reasoning>} */
        public static final String RULE_PASSED_FORMAT = "%s rule matched: %s";
        /** {@code <Required|Supporting|Exclusion> rule did not pass: <reasoning>} */
        public static final String RULE_FAILED_FORMAT = "%s rule did not pass: %s";
        /** {@code <signalName>: <signalValue>} */
        public static final String SIGNAL_SNIPPET_FORMAT = "%s: %s";
    }

    /**
     * Identifier prefixes for generated evidence item IDs.
     */
    public static final class Ids {
        private Ids() {}

        public static final String PATH_A_PREFIX = "pathA.";
        public static final String PATH_B_PREFIX = "pathB.";
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
     * Rule-weight bands used to derive {@code EvidenceStrength} from PATH B rules.
     * One band per {@code EvidenceStrength}, each the inclusive floor of its band.
     */
    public static final class RuleWeightBands {
        private RuleWeightBands() {}

        /** Definitive proof. */
        public static final int DEFINITIVE     = 10;
        /** Strong indicator. */
        public static final int STRONG         = 5;
        /** Moderate indicator. */
        public static final int MODERATE       = 3;
        /** Weak indicator — any rule carrying weight at all. */
        public static final int WEAK           = 1;
        /** Circumstantial — a weightless rule, which evidences nothing on its own. */
        public static final int CIRCUMSTANTIAL = 0;
    }

    /**
     * Confidence assigned to PATH B evidence, which carries no relevance score of its own.
     */
    public static final class RuleConfidence {
        private RuleConfidence() {}

        public static final double PASSED_REQUIRED    = 0.95;
        public static final double PASSED_SUPPORTING  = 0.75;
        public static final double REFUTED            = 0.90;
        public static final double RULED_OUT          = 0.50;
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
