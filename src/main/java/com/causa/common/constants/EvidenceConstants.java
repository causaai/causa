package com.causa.common.constants;

/**
 * Evidence Constants
 *
 * <p>Constants for the evidence collection pipeline — canonical MCP source names,
 * metadata keys, identifier prefixes, and the thresholds used to derive evidence
 * strength, priority, and user-facing reliability labels.
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
        public static final String PATH_B               = "B";
        public static final String FINDING_ID           = "findingId";
        public static final String ANOMALY_TYPE         = "anomalyType";

        // PATH A
        public static final String ASSERTION_ID         = "assertionId";
        public static final String ASSERTION_TYPE       = "assertionType";
        public static final String ASSERTION_STATUS     = "assertionStatus";
        /** The evidence source string exactly as the LLM emitted it, before normalisation. */
        public static final String RAW_SOURCE_LABEL     = "rawSourceLabel";

        // PATH B
        public static final String RULE_ID              = "ruleId";
        public static final String RULE_TYPE            = "ruleType";
        public static final String RULE_WEIGHT          = "weight";
        public static final String RULE_PASSED          = "passed";
        public static final String INSPECTED_SIGNAL     = "inspectedSignal";

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

        /**
         * Key under which {@code DiagnosticContextSignalExtractor} stamps the lines surrounding
         * the match. This is what {@code EvidenceItem.contextSnippet} carries: the line alone
         * proves the value was there, the window around it shows what else the source was
         * saying at that point.
         */
        public static final String SIGNAL_CONTEXT       = "context";
    }

    /**
     * Reasoning text rendered onto evidence derived from rule evaluation.
     */
    public static final class Messages {
        private Messages() {}

        // The rule description is NOT interpolated into either format: the evaluation reasoning
        // already names what was looked for, and the rule itself is identified by Metadata.RULE_ID.

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
        /** Ruled-out alternatives — recorded, but never surfaced. */
        public static final int BACKGROUND   = 8;
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
     * Rule-weight bands used to derive {@code EvidenceStrength} from PATH B rules.
     */
    public static final class RuleWeightBands {
        private RuleWeightBands() {}

        public static final int DEFINITIVE = 10;
        public static final int STRONG     = 5;
        public static final int MODERATE   = 3;
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
     * User-facing reliability labels rendered into {@code DiagnosticDetailResponse.Evidence}.
     */
    public static final class Reliability {
        private Reliability() {}

        public static final String HIGH           = "High";
        public static final String MEDIUM         = "Medium";
        public static final String LOW            = "Low";
        public static final String REFUTES_SUFFIX = " — refutes";
    }

    /**
     * Limits applied when selecting the user-facing evidence panel.
     */
    public static final class Selection {
        private Selection() {}

        /**
         * Ceiling on evidence entries rendered in the API response — not a target. The panel
         * stops early whenever the diagnosis has fewer solid facts than slots.
         */
        public static final int MAX_UI_EVIDENCE   = 15;
        /**
         * Supporting entries below which weaker evidence is allowed to fill in.
         *
         * <p>Above it the panel shows only DEFINITIVE and STRONG items and ends short of the
         * budget. Nothing upstream checks that an LLM-supplied snippet actually speaks to the
         * claim it is attached to, so padding the remaining slots surfaces things like a
         * startup banner cited as proof of allocation retention — which discredits the solid
         * entries beside it. Below the floor the finding has too little backing to stand on
         * its own, and weak evidence beats a panel that reads as though nothing was checked.
         */
        public static final int MIN_SUPPORTING    = 3;
        /** Maximum REFUTES entries — contradictions matter, but must not crowd out the finding. */
        public static final int MAX_REFUTING      = 3;
    }

    /**
     * Sentinel text and truncation limits for snippets.
     */
    public static final class Snippet {
        private Snippet() {}

        /** Maximum rawSnippet length retained on an EvidenceItem. */
        public static final int MAX_LENGTH = 2000;
        public static final String TRUNCATION_SUFFIX = "…";

        /**
         * Lines kept either side of a match when capturing its surrounding context.
         *
         * <p>Deliberately small. The containing section is the honest unit of context, but a
         * pod-logs section runs to thousands of lines — a window is what fits in a response
         * and in a reader's attention.
         */
        public static final int CONTEXT_LINES = 2;
    }
}
