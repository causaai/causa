package com.causa.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An LLM quote is not a substring. It elides with an ellipsis, joins lines that were never
 * adjacent, and appends its own commentary — so locating one means finding the longest piece of
 * it that survived verbatim, and refusing to guess when none did.
 */
class DiagnosticContextIndexTest {

    private static final String CONTEXT = """
        --- POD STATUS ---
        Last Terminated State:
          Reason: OOMKilled
          Exit Code: 137

        --- POD LOGS (recent) ---
        13:31:33 INFO  warmup complete
        13:31:35 INFO  [request-mode] req=47 alloc=2104786B retainedChunks=123
        13:31:37 INFO  [request-mode] req=48 alloc=2089150B retainedChunks=125
        13:31:39 INFO  [request-mode] req=49 alloc=2089150B retainedChunks=127
        """;

    private final DiagnosticContextIndex index = DiagnosticContextIndex.of(CONTEXT);

    @Test
    void locatesAnElidedQuoteByItsLongestVerbatimFragment() {
        String context = index.contextAround(
            "alloc=2104786B ... alloc=2089150B (all values ~2MB across 25 logged requests)");

        assertThat(context)
            .contains("req=47")
            .contains("req=48")
            .contains("warmup complete");
    }

    @Test
    void returnsNullWhenTheQuoteIsNowhereInTheContext() {
        assertThat(index.contextAround("heap dump written to /tmp/dump.hprof")).isNull();
    }

    @Test
    void refusesToAnchorOnAFragmentTooShortToBeProvenance() {
        // "req=" occurs on every log line; a window built from it would be arbitrary.
        assertThat(index.contextAround("req= ... INFO")).isNull();
    }

    @Test
    void keepsTheWindowInsideTheSectionTheQuoteCameFrom() {
        String context = index.contextAround("Reason: OOMKilled");

        assertThat(context)
            .contains("Exit Code: 137")
            .doesNotContain("POD LOGS")
            .doesNotContain("warmup complete");
    }

    @Test
    void toleratesAnAbsentContext() {
        DiagnosticContextIndex empty = DiagnosticContextIndex.of(null);

        assertThat(empty.contextAround("Reason: OOMKilled")).isNull();
        assertThat(empty.labelAt(0)).isNull();
    }
}
