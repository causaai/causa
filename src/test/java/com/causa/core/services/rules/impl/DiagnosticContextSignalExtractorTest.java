package com.causa.core.services.rules.impl;

import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.common.constants.ValidationConstants.SignalNames;
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
        assertThat(snippetOf("exitCode")).isEqualTo("Exit Code: 137");
    }

    @Test
    void attributesEachSignalToItsSection() {
        assertThat(metadataOf("exitCode", Metadata.SIGNAL_SECTION))
            .contains("POD STATUS");
    }

    @Test
    void extractsOOMKilledFromInstanaK8sEvents() {
        String contextWithInstana = """
            --- POD STATUS ---
            Name: quarkus-app-7d9f8b6c5-abcde

            --- INSTANA_K8S_EVENTS ---
            {
              "results": {
                "events": [
                  {
                    "eventId": "evt-oom-001",
                    "entityLabel": "default/quarkus-app-7d9f8b6c5-abcde",
                    "title": "OOMKilled",
                    "probableCause": {
                      "problem": "OOMKilled: Java heap space exhaustion with exit code 137"
                    }
                  }
                ],
                "problem_groups": [
                  {
                    "groupName": "OOMKilled",
                    "occurrences": 1
                  }
                ]
              }
            }
            """;

        List<Signal> signals = extractor.extractSignals(contextWithInstana);

        assertThat(signals)
            .anySatisfy(s -> assertThat(s.getName())
                .isEqualTo(SignalNames.TERMINATION_REASON));
        assertThat(signalValue(signals, SignalNames.TERMINATION_REASON))
            .isPresent()
            .hasValue("OOMKilled");
        assertThat(signals)
            .anySatisfy(s -> assertThat(s.getName())
                .isEqualTo(SignalNames.EXIT_CODE));
        assertThat(signalValue(signals, SignalNames.EXIT_CODE))
            .isPresent()
            .hasValue(137);
    }

    @Test
    void extractsMemoryTrendFromInstanaJvmMetrics() {
        String contextWithInstana = """
            --- POD STATUS ---
            Name: quarkus-app-7d9f8b6c5-abcde

            --- INSTANA_JVM_METRICS ---
            {
              "results": {
                "metrics": [
                  {
                    "timestamp": 1726920000000,
                    "values": [
                      {"metric": "memory.used", "value": 157286400, "unit": "bytes"},
                      {"metric": "memory.max", "value": 218955776, "unit": "bytes"}
                    ]
                  },
                  {
                    "timestamp": 1726920120000,
                    "values": [
                      {"metric": "memory.used", "value": 210763776, "unit": "bytes"},
                      {"metric": "memory.max", "value": 218955776, "unit": "bytes"}
                    ]
                  }
                ]
              }
            }
            """;

        List<Signal> signals = extractor.extractSignals(contextWithInstana);

        assertThat(signalValue(signals, SignalNames.MEMORY_UTILIZATION_TREND))
            .isPresent()
            .hasValue("INCREASING");
        assertThat(signalValue(signals, SignalNames.HEAP_USAGE_RATIO))
            .isPresent();
        double ratio = (double) signalValue(signals, SignalNames.HEAP_USAGE_RATIO).orElseThrow();
        assertThat(ratio).isGreaterThan(0.95);  // 210763776 / 218955776 ≈ 0.96
    }

    @Test
    void doesNotExtractMemoryTrendWhenMetricsDecreasing() {
        String contextWithInstana = """
            --- POD STATUS ---
            Name: quarkus-app-7d9f8b6c5-abcde

            --- INSTANA_JVM_METRICS ---
            {
              "results": {
                "metrics": [
                  {
                    "timestamp": 1726920000000,
                    "values": [
                      {"metric": "memory.used", "value": 210763776, "unit": "bytes"},
                      {"metric": "memory.max", "value": 218955776, "unit": "bytes"}
                    ]
                  },
                  {
                    "timestamp": 1726920120000,
                    "values": [
                      {"metric": "memory.used", "value": 157286400, "unit": "bytes"},
                      {"metric": "memory.max", "value": 218955776, "unit": "bytes"}
                    ]
                  }
                ]
              }
            }
            """;

        List<Signal> signals = extractor.extractSignals(contextWithInstana);

        assertThat(signals)
            .noneMatch(s -> s.getName().equals(SignalNames.MEMORY_UTILIZATION_TREND));
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

    private Optional<Object> signalValue(List<Signal> signals, String signalName) {
        return signals.stream()
            .filter(s -> s.getName().equals(signalName))
            .findFirst()
            .map(Signal::getValue);
    }
}
