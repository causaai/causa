package com.causa.core.services.rules.impl;

import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.core.services.rules.Signal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signals are matched against a flattened diagnostic context, so everything a reader needs to
 * judge a signal has to be captured at match time: the line it was read from, the lines around
 * it, and the section both came from. None of it is recoverable afterwards.
 */
class DiagnosticContextSignalExtractorTest {

    private static final String CONTEXT = """
        --- POD STATUS ---
        Name: causa-app-7d9f
        Reason: OOMKilled
        Exit Code: 137
        Restart Count: 4

        --- POD LOGS (recent) ---
        13:31:36 INFO  starting allocation round
        13:31:37 INFO  remaining=110724944B max=218955776B
        13:31:38 WARN  allocation slowing
        """;

    private final DiagnosticContextSignalExtractor extractor = new DiagnosticContextSignalExtractor();

    @Test
    void quotesTheContextLineEachSignalWasReadFrom() {
        assertThat(snippetOf("exitCode")).contains("Exit Code: 137");
    }

    @Test
    void capturesTheLinesSurroundingTheMatch() {
        String context = metadataOf("exitCode", Metadata.SIGNAL_CONTEXT).orElseThrow();

        assertThat(context)
            .contains("Reason: OOMKilled")
            .contains("Exit Code: 137")
            .contains("Restart Count: 4");
    }

    @Test
    void stopsTheWindowAtTheSectionBoundary() {
        // Exit Code sits two lines from the end of POD STATUS; an unclamped window would run
        // into POD LOGS and attribute one source's output to another's evidence.
        String context = metadataOf("exitCode", Metadata.SIGNAL_CONTEXT).orElseThrow();

        assertThat(context)
            .doesNotContain("POD LOGS")
            .doesNotContain("starting allocation round");
    }

    @Test
    void attributesEachSignalToItsSection() {
        assertThat(metadataOf("exitCode", Metadata.SIGNAL_SECTION))
            .contains("POD STATUS");
    }

    private String snippetOf(String signalName) {
        return metadataOf(signalName, Metadata.SIGNAL_SNIPPET).orElseThrow();
    }

    private Optional<String> metadataOf(String signalName, String key) {
        List<Signal> signals = extractor.extractSignals(CONTEXT);
        return signals.stream()
            .filter(s -> s.getName().equals(signalName))
            .findFirst()
            .flatMap(s -> s.getMetadata(key))
            .map(Object::toString);
    }
}
