package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.Assertion.AssertionSource;
import com.causa.core.domain.validation.Assertion.AssertionType;
import com.causa.core.domain.validation.DualValidationResult;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.ValidatedRCA;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.evidence.EvidenceCollectionResult;
import com.causa.core.services.evidence.EvidenceHarvester;
import com.causa.core.services.evidence.McpSourceResolver;
import com.causa.core.services.evidence.RcaFinding;
import com.causa.core.services.rules.HypothesisValidationResult;
import com.causa.core.services.rules.Rule;
import com.causa.core.services.rules.RuleEvaluationResult;
import com.causa.core.services.rules.RuleType;
import com.causa.core.services.rules.Signal;
import com.causa.mcp.McpRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The harvester is anomaly-agnostic — the mapper reads validation output, never the
 * hypothesis — so the gate is the only place a kind of finding can be turned away.
 */
class DualPathEvidenceHarvesterTest {

    private final EvidenceHarvester harvester = new DualPathEvidenceHarvester();

    @Test
    void harvestsEveryDiagnosedAnomaly() {
        assertThat(harvester.supports(AnomalyType.OOM_KILLED)).isTrue();
        assertThat(harvester.supports(AnomalyType.POSSIBLE_OOM_KILLED)).isTrue();
        assertThat(harvester.supports(AnomalyType.POSSIBLE_GC_PAUSE)).isTrue();
    }

    /**
     * A finding that claims nothing went wrong has nothing to evidence, and an unclassified one
     * has no ruleset behind it either. Both yield a skip rather than an empty evidence panel.
     */
    @Test
    void harvestsNothingWhenThereIsNoAnomalyToEvidence() {
        assertThat(harvester.supports(AnomalyType.HEALTHY)).isFalse();
        assertThat(harvester.supports(null)).isFalse();
    }

    /** Rule outcomes reach the harvest, not just LLM assertions. */
    @Test
    void harvestsRuleOutcomesFromTheRuleBasedVerdict() {
        EvidenceCollectionResult result = wiredHarvester().harvest(
            FINDING, validatedRca(ruleVerdictThatMatched()));

        assertThat(result.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.id()).isEqualTo("pathB.exit.code");
                assertThat(item.rawSnippet()).isEqualTo("Exit Code: 137");
            });
    }

    /**
     * PATH A alone still produces a harvest — a run where the engine had no ruleset for the
     * anomaly keeps the assertion evidence rather than dropping it with PATH B.
     */
    @Test
    void harvestsPathAWithoutARuleBasedVerdict() {
        EvidenceCollectionResult result = wiredHarvester().harvest(
            FINDING, validatedRca(null, supportedAssertion()));

        assertThat(result.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.id()).isEqualTo("pathA.a1.ev-01");
                assertThat(item.rawSnippet()).isEqualTo("Exit Code: 137");
            });
    }

    /** The two paths are additive — one harvest carries both, PATH A first. */
    @Test
    void mergesBothPathsIntoOneHarvest() {
        EvidenceCollectionResult result = wiredHarvester().harvest(
            FINDING, validatedRca(ruleVerdictThatMatched(), supportedAssertion()));

        assertThat(result.items()).extracting(EvidenceItem::id)
            .containsExactly("pathA.a1.ev-01", "pathB.exit.code");
    }

    /** Neither path ran: an empty result, not a failure. */
    @Test
    void harvestsNothingWhenNeitherPathProducedOutput() {
        assertThat(wiredHarvester().harvest(FINDING, validatedRca(null)).isEmpty()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static final RcaFinding FINDING =
        new RcaFinding("diag_test", AnomalyType.OOM_KILLED, "Container OOM killed");

    private DualPathEvidenceHarvester wiredHarvester() {
        McpSourceResolver resolver = new McpSourceResolver(new McpRegistry(null));
        DualPathEvidenceHarvester wired = new DualPathEvidenceHarvester();
        wired.pathAMapper = new PathAEvidenceMapper(resolver);
        wired.pathBMapper = new PathBEvidenceMapper(resolver);
        return wired;
    }

    private ValidatedRCA validatedRca(HypothesisValidationResult ruleVerdict, ValidationResult... pathA) {
        return ValidatedRCA.builder()
            .originalRca(new RootCauseAnalysis(
                "Container OOM killed", null, null, null,
                AnomalyType.OOM_KILLED, null, null, null, null, null, null))
            .validationResults(List.of(pathA))
            .dualValidation(ruleVerdict == null
                ? null
                : new DualValidationResult(null, ruleVerdict, null))
            .build();
    }

    private ValidationResult supportedAssertion() {
        return ValidationResult.supported(
            Assertion.of("a1", "Container was OOMKilled",
                AssertionType.OBSERVATION, AssertionSource.ROOT_CAUSE),
            0.9,
            List.of(Evidence.of("POD_STATUS", Evidence.EvidenceType.KUBERNETES_EVENT,
                "Exit Code: 137", 0.9)),
            "exit code 137 observed");
    }

    private HypothesisValidationResult ruleVerdictThatMatched() {
        Signal exitCode = Signal.builder(Signal.SignalType.CONTAINER_STATUS, "exitCode")
            .value(137)
            .metadata(EvidenceConstants.Metadata.SIGNAL_SNIPPET, "Exit Code: 137")
            .build();

        Rule rule = new Rule.BaseRule("exit.code", "Exit code is 137", RuleType.REQUIRED, 40) {
            @Override
            public RuleEvaluationResult evaluate(List<Signal> signals) {
                throw new UnsupportedOperationException("not evaluated in this test");
            }
        };

        return HypothesisValidationResult.builder("OOM_KILLED")
            .requiredResults(List.of(
                RuleEvaluationResult.passed(rule, List.of(exitCode), "Exit code 137 observed")))
            .build();
    }
}
