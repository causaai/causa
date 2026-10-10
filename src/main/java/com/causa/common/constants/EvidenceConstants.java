package com.causa.common.constants;

/**
 * Evidence Constants
 *
 * <p>Constants for the evidence collection pipeline. Only signal provenance is here today;
 * the source names, identifier prefixes, strength bands and selection limits land with the
 * mappers and selector that read them.
 *
 * @since 0.0.1
 */
public final class EvidenceConstants {

    private EvidenceConstants() {
        // Prevent instantiation
    }

    /**
     * Keys used in {@code EvidenceItem.metadata}.
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

        /**
         * Key under which {@code DiagnosticContextSignalExtractor} stamps the lines surrounding
         * the match. This is what {@code EvidenceItem.contextSnippet} carries: the line alone
         * proves the value was there, the window around it shows what else the source was
         * saying at that point.
         */
        public static final String SIGNAL_CONTEXT       = "context";
    }

    /**
     * Sentinel text and truncation limits for snippets.
     */
    public static final class Snippet {
        private Snippet() {}

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
