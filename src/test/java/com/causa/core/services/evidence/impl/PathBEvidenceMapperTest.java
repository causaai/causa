package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.common.constants.EvidenceConstants.Sources;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.services.evidence.McpSourceResolver;
import com.causa.core.services.rules.Rule;
import com.causa.core.services.rules.RuleEvaluationResult;
import com.causa.core.services.rules.RuleType;
import com.causa.core.services.rules.Signal;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source attribution on PATH B comes from the signal that was actually read, never from the
 * rule. Rules match on signal type and name only: the same signal can arrive from several MCP
 * servers, and servers are plug-and-play, so a rule cannot know which one should have answered.
 */
class PathBEvidenceMapperTest {

    /** Section labels are {@code contextKey}s from mcp.json — the resolver reads that, not a table. */
    private static final String POD_STATUS = "POD_STATUS";
    private static final String CRYOSTAT_ANALYSIS = "CRYOSTAT_ANALYSIS";
    private static final String QUARKUS_RAW_METRICS = "QUARKUS_RAW_METRICS";

    private final PathBEvidenceMapper mapper = new PathBEvidenceMapper(new McpSourceResolver(registry()));

    private static McpRegistry registry() {
        McpRegistry registry = new McpRegistry(null);
        registry.init(new McpSettings(Map.of(
            Sources.KUBERNETES, server(POD_STATUS),
            Sources.CRYOSTAT, server(CRYOSTAT_ANALYSIS),
            Sources.QUARKUS, server(QUARKUS_RAW_METRICS))));
        return registry;
    }

    private static McpSettings.ServerConfig server(String contextKey) {
        return new McpSettings.ServerConfig(
            "streamable-http", "http://localhost/mcp", Map.of(), false,
            new McpSettings.HealthCheckConfig("http://localhost/healthz", 1000), 1000,
            Map.of(), null,
            List.of(new McpSettings.ToolConfig("tool", contextKey, null, Map.of())));
    }

    @Test
    void attributesEvidenceToTheSectionTheSignalWasReadFrom() {
        Signal signal = Signal.builder(Signal.SignalType.CONTAINER_STATUS, "exitCode")
            .value(137)
            .metadata(Metadata.SIGNAL_SECTION, POD_STATUS)
            .build();

        EvidenceItem item = mapper.map(null,
            RuleEvaluationResult.passed(rule(RuleType.REQUIRED), List.of(signal), "matched"));

        assertThat(item.source()).isEqualTo(Sources.KUBERNETES);
    }

    /**
     * Interim behaviour — see {@code docs/path-b-evidence-statement-design.md}. The statement is
     * the rule's own outcome message, so it reads as prose rather than as the log line in
     * {@code rawSnippet}. The two must not collapse into each other.
     */
    @Test
    void statesTheRuleOutcomeRatherThanRepeatingTheLogLine() {
        Signal signal = Signal.builder(Signal.SignalType.CONTAINER_STATUS, "exitCode")
            .value(137)
            .metadata(Metadata.SIGNAL_SNIPPET, "Exit Code: 137")
            .build();

        EvidenceItem item = mapper.map(null, RuleEvaluationResult.passed(
            rule(RuleType.REQUIRED), List.of(signal), "Found container exit code 137"));

        assertThat(item.statement()).isEqualTo("Found container exit code 137");
        assertThat(item.rawSnippet()).isEqualTo("Exit Code: 137");
    }

    @Test
    void followsTheSignalWhenTheSameRuleIsAnsweredByADifferentServer() {
        Signal fromCryostat = signalFrom(CRYOSTAT_ANALYSIS);
        Signal fromQuarkus = signalFrom(QUARKUS_RAW_METRICS);
        Rule rule = rule(RuleType.SUPPORTING);

        assertThat(mapper.map(null, RuleEvaluationResult.passed(rule, List.of(fromCryostat), "ok")).source())
            .isEqualTo(Sources.CRYOSTAT);
        assertThat(mapper.map(null, RuleEvaluationResult.passed(rule, List.of(fromQuarkus), "ok")).source())
            .isEqualTo(Sources.QUARKUS);
    }

    /**
     * A rule that inspected nothing read no source output, so it has none to show. Mapping it
     * anyway would put an item on the panel whose snippet is the rule's own restated name.
     */
    @Test
    void producesNoEvidenceWhenNoSignalWasInspected() {
        assertThat(mapper.map(null,
            RuleEvaluationResult.failed(rule(RuleType.REQUIRED), "no such signal"))).isNull();
    }

    @Test
    void quotesTheContextLineTheSignalWasReadFrom() {
        Signal signal = Signal.builder(Signal.SignalType.CONTAINER_STATUS, "exitCode")
            .value(137)
            .metadata(Metadata.SIGNAL_SNIPPET, "Exit Code: 137")
            .build();

        EvidenceItem item = mapper.map(null,
            RuleEvaluationResult.passed(rule(RuleType.REQUIRED), List.of(signal), "matched"));

        assertThat(item.rawSnippet()).isEqualTo("Exit Code: 137");
    }

    @Test
    void fallsBackToTheNormalisedPairWhenTheSignalCarriesNoContextLine() {
        EvidenceItem item = mapper.map(null, RuleEvaluationResult.passed(
            rule(RuleType.REQUIRED),
            List.of(signalFrom(CRYOSTAT_ANALYSIS)),
            "matched"));

        assertThat(item.rawSnippet()).isEqualTo("heap.usage.trend: INCREASING");
    }

    private Signal signalFrom(String section) {
        return Signal.builder(Signal.SignalType.METRIC, "heap.usage.trend")
            .value("INCREASING")
            .metadata(Metadata.SIGNAL_SECTION, section)
            .build();
    }

    private Rule rule(RuleType type) {
        return new Rule.BaseRule("test.rule", "A test rule", type, 10) {
            @Override
            public RuleEvaluationResult evaluate(List<Signal> signals) {
                throw new UnsupportedOperationException("not evaluated in this test");
            }
        };
    }
}
