package com.causa.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A signal is matched against the flattened context and remembers nothing of where it came
 * from, so the index has to map an offset back to the section that produced it and the line it
 * was read from. Attributing an offset to the wrong section would credit one MCP server with
 * another's output.
 */
class DiagnosticContextIndexTest {

    private static final String CONTEXT = """
        Platform: cluster

        --- POD STATUS ---
        Last Terminated State:
          Reason: OOMKilled
          Exit Code: 137

        --- POD LOGS (recent) ---
        13:31:33 INFO  warmup complete
        13:31:35 INFO  [request-mode] req=47 alloc=2104786B retainedChunks=123
        """;

    private final DiagnosticContextIndex index = DiagnosticContextIndex.of(CONTEXT);

    @Test
    void attributesAnOffsetToTheSectionContainingIt() {
        assertThat(index.labelAt(CONTEXT.indexOf("Exit Code: 137"))).isEqualTo("POD STATUS");
        assertThat(index.labelAt(CONTEXT.indexOf("warmup complete"))).isEqualTo("POD LOGS (recent)");
    }

    /** The preamble precedes every section header, so it belongs to no source. */
    @Test
    void attributesNothingToTextOutsideAnySection() {
        assertThat(index.labelAt(CONTEXT.indexOf("Platform: cluster"))).isNull();
        assertThat(index.labelAt(-1)).isNull();
    }

    /**
     * The whole line, not the match: {@code "137"} alone proves nothing, while
     * {@code "Exit Code: 137"} is the text the source actually emitted.
     */
    @Test
    void returnsTheWholeLineTheOffsetFallsOn() {
        assertThat(index.lineAt(CONTEXT.indexOf("137"))).isEqualTo("Exit Code: 137");
    }

    @Test
    void readsNoLineFromAnOffsetOutsideTheContext() {
        assertThat(index.lineAt(-1)).isNull();
        assertThat(index.lineAt(CONTEXT.length())).isNull();
    }

    @Test
    void toleratesAnAbsentContext() {
        DiagnosticContextIndex empty = DiagnosticContextIndex.of(null);

        assertThat(empty.labelAt(0)).isNull();
        assertThat(empty.lineAt(0)).isNull();
    }
}
