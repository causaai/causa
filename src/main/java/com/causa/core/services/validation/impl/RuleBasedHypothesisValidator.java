package com.causa.core.services.validation.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.services.rules.*;
import com.causa.core.services.rules.impl.DiagnosticContextSignalExtractor;
import com.causa.core.services.rules.oom.OomKilledRuleSet;
import com.causa.core.services.validation.HypothesisValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Rule-Based Hypothesis Validator.
 *
 * <p>Deterministic validation of RCA hypotheses using pattern matching and scoring rules.
 *
 * <p><strong>Process:</strong>
 * <ol>
 *   <li>Extract signals from diagnostic context</li>
 *   <li>Identify hypothesis from RCA (e.g., "OOMKilled", "MemoryLeak")</li>
 *   <li>Load appropriate rule set</li>
 *   <li>Evaluate REQUIRED rules (gating conditions)</li>
 *   <li>Evaluate SUPPORTING rules (add positive weight)</li>
 *   <li>Evaluate EXCLUSION rules (subtract weight)</li>
 *   <li>Calculate weighted score</li>
 *   <li>Map to verdict: SUPPORTED, PARTIALLY_SUPPORTED, or UNSUPPORTED</li>
 * </ol>
 *
 * <p><strong>Parallel Execution:</strong>
 * This validator runs in parallel with assertion-based validation:
 * <ul>
 *   <li>Assertion Path: Extract assertions → Validate each with LLM</li>
 *   <li>Hypothesis Path: Validate hypothesis directly with rules</li>
 * </ul>
 *
 * <p>Both results are combined in ValidatedRCA for dual confidence scoring.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class RuleBasedHypothesisValidator implements HypothesisValidator {

    private static final CausaLogger log = CausaLogger.getLogger(RuleBasedHypothesisValidator.class);

    private final RuleEngine ruleEngine;
    private final SignalExtractor signalExtractor;
    private final OomKilledRuleSet oomKilledRuleSet;

    @Inject
    public RuleBasedHypothesisValidator(
        RuleEngine ruleEngine,
        DiagnosticContextSignalExtractor signalExtractor,
        OomKilledRuleSet oomKilledRuleSet
    ) {
        this.ruleEngine = ruleEngine;
        this.signalExtractor = signalExtractor;
        this.oomKilledRuleSet = oomKilledRuleSet;
    }

    @Override
    public HypothesisValidationResult validateHypothesis(
        RootCauseAnalysis rca,
        String diagnosticContext
    ) {
        log.info("Validating RCA hypothesis with rule-based approach")
            .field("issueTitle", rca.issueTitle())
            .field("anomalyType", rca.anomalyType())
            .log();

        try {
            // Step 1: Extract signals from diagnostic context
            List<Signal> signals = signalExtractor.extractSignals(diagnosticContext);

            log.debug("Signals extracted from diagnostic context")
                .field("signalCount", signals.size())
                .log();

            // Step 2: Determine hypothesis from RCA
            String hypothesis = determineHypothesis(rca);

            log.debug("Hypothesis identified")
                .field("hypothesis", hypothesis)
                .log();

            // Step 3: Load appropriate rule set
            RuleSet ruleSet = loadRuleSet(hypothesis);

            if (ruleSet == null) {
                log.warn("No rule set available for hypothesis")
                    .field("hypothesis", hypothesis)
                    .field("anomalyType", rca.anomalyType())
                    .log();

                // Return UNKNOWN verdict
                return HypothesisValidationResult.builder(hypothesis)
                    .status(HypothesisValidationResult.ValidationStatus.UNSUPPORTED)
                    .confidence(0.0)
                    .totalScore(0)
                    .requiredResults(List.of())
                    .supportingResults(List.of())
                    .exclusionResults(List.of())
                    .explanation("No rule set available for hypothesis: " + hypothesis)
                    .build();
            }

            // Step 4: Evaluate hypothesis using rule engine
            HypothesisValidationResult result = ruleEngine.validate(hypothesis, ruleSet, signals);

            log.info("Rule-based hypothesis validation completed")
                .field("hypothesis", hypothesis)
                .field("status", result.getStatus())
                .field("confidence", result.getConfidence())
                .field("score", result.getTotalScore())
                .field("requiredPassed", result.getRequiredPassed())
                .field("requiredTotal", result.getRequiredTotal())
                .field("supportingMatched", result.getSupportingMatched())
                .field("exclusionMatched", result.getExclusionMatched())
                .log();

            return result;

        } catch (Exception e) {
            log.error("Rule-based hypothesis validation failed")
                .field("issueTitle", rca.issueTitle())
                .exception(e)
                .log();

            // Return failed validation
            String hypothesis = determineHypothesis(rca);
            return HypothesisValidationResult.builder(hypothesis)
                .status(HypothesisValidationResult.ValidationStatus.UNSUPPORTED)
                .confidence(0.0)
                .totalScore(0)
                .requiredResults(List.of())
                .supportingResults(List.of())
                .exclusionResults(List.of())
                .explanation("Validation failed: " + e.getMessage())
                .build();
        }
    }

    /**
     * Determine hypothesis from RCA.
     *
     * <p>Maps RCA fields to known hypotheses using pattern matching.
     */
    private String determineHypothesis(RootCauseAnalysis rca) {
        // Check anomaly type first
        RootCauseAnalysis.AnomalyType anomalyType = rca.anomalyType();
        if (anomalyType != null) {
            String normalized = anomalyType.name().toUpperCase().replace(" ", "_");
            if (normalized.contains("OOM") || normalized.contains("OUT_OF_MEMORY")) {
                return "OOMKilled";
            }
            if (normalized.contains("MEMORY_LEAK")) {
                return "MemoryLeak";
            }
            if (normalized.contains("GC") && normalized.contains("PAUSE")) {
                return "GCPause";
            }
        }

        // Check issue title
        String issueTitle = rca.issueTitle();
        if (issueTitle != null) {
            String lower = issueTitle.toLowerCase();
            if (lower.contains("oomkilled") ||
                lower.contains("out of memory") ||
                lower.contains("exit code 137")) {
                return "OOMKilled";
            }
            if (lower.contains("memory leak")) {
                return "MemoryLeak";
            }
            if (lower.contains("gc pause")) {
                return "GCPause";
            }
        }

        // Check root cause text
        String rootCause = rca.rootCause();
        if (rootCause != null) {
            String lower = rootCause.toLowerCase();
            if (lower.contains("oomkilled") ||
                lower.contains("killed due to") && lower.contains("memory")) {
                return "OOMKilled";
            }
        }

        // Default fallback
        return anomalyType != null ? anomalyType.name() : "UNKNOWN";
    }

    /**
     * Load rule set for hypothesis.
     *
     * <p>Maps hypothesis names to their corresponding rule sets.
     *
     * <p>Currently supports:
     * <ul>
     *   <li>OOMKilled</li>
     * </ul>
     *
     * <p>TODO: Add MemoryLeak, GCPause, CPU Throttling, etc.
     */
    private RuleSet loadRuleSet(String hypothesis) {
        return switch (hypothesis.toUpperCase()) {
            case "OOMKILLED", "OOM_KILLED", "OUT_OF_MEMORY" -> oomKilledRuleSet;
            // TODO: Add more rule sets
            // case "MEMORYLEAK", "MEMORY_LEAK" -> memoryLeakRuleSet;
            // case "GCPAUSE", "GC_PAUSE" -> gcPauseRuleSet;
            default -> {
                log.debug("No rule set for hypothesis")
                    .field("hypothesis", hypothesis)
                    .log();
                yield null;
            }
        };
    }
}
