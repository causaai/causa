package com.causa.core.services.validation.impl;

import com.causa.common.constants.ValidationConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.*;
import com.causa.core.services.rules.HypothesisValidationResult;
import com.causa.core.services.validation.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of RCA validation using DUAL validation paths.
 *
 * <p><strong>PATH A (Assertion-based):</strong>
 * <ol>
 *   <li>Extract assertions from RCA</li>
 *   <li>Validate each assertion using LLM</li>
 *   <li>Aggregate assertion results</li>
 * </ol>
 *
 * <p><strong>PATH B (Rule-based):</strong>
 * <ol>
 *   <li>Extract signals from diagnostic context</li>
 *   <li>Validate hypothesis using deterministic rules</li>
 * </ol>
 *
 * <p><strong>FINAL:</strong> Aggregate both paths into final verdict.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class RcaValidatorImpl implements RcaValidator {

    private static final CausaLogger log = CausaLogger.getLogger(RcaValidatorImpl.class);

    // Default configuration
    private static final double DEFAULT_MIN_CONFIDENCE = 0.5;
    private static final double STRONG_SUPPORT_THRESHOLD = 0.8;
    private static final double WEAK_SUPPORT_THRESHOLD = 0.4;
    private static final DualValidationResult.FinalVerdict.AggregationStrategy DEFAULT_STRATEGY =
        DualValidationResult.FinalVerdict.AggregationStrategy.WEIGHTED_AVERAGE;

    private final AssertionExtractor assertionExtractor;
    private final EvidenceMatcher evidenceMatcher;
    private final Optional<AssertionAnalyzer> assertionAnalyzer;
    private final Optional<HypothesisValidator> hypothesisValidator;
    private final ValidationAggregator validationAggregator;

    @Inject
    public RcaValidatorImpl(
        AssertionExtractor assertionExtractor,
        EvidenceMatcher evidenceMatcher,
        Instance<AssertionAnalyzer> assertionAnalyzerInstance,
        Instance<HypothesisValidator> hypothesisValidatorInstance,
        ValidationAggregator validationAggregator
    ) {
        this.assertionExtractor = assertionExtractor;
        this.evidenceMatcher = evidenceMatcher;
        this.assertionAnalyzer = assertionAnalyzerInstance.isResolvable() ?
            Optional.of(assertionAnalyzerInstance.get()) : Optional.empty();
        this.hypothesisValidator = hypothesisValidatorInstance.isResolvable() ?
            Optional.of(hypothesisValidatorInstance.get()) : Optional.empty();
        this.validationAggregator = validationAggregator;
    }

    @Override
    public ValidatedRCA validate(RootCauseAnalysis rca, String diagnosticContext) {
        return validate(rca, diagnosticContext, DEFAULT_MIN_CONFIDENCE);
    }

    @Override
    public ValidatedRCA validate(
        RootCauseAnalysis rca,
        String diagnosticContext,
        double minimumConfidence
    ) {
        String separator = "=".repeat(80);

        log.info("\n" + separator + "\n" +
                 "🔍 DUAL VALIDATION PIPELINE STARTED\n" +
                 separator)
            .field("issueTitle", rca.issueTitle())
            .field("anomalyType", rca.anomalyType())
            .field("minimumConfidence", minimumConfidence)
            .log();

        // Log full RCA input
        log.info("\n" + separator + "\n" +
                 "📊 RCA INPUT TO VALIDATION:\n" +
                 separator + "\n" +
                 "Issue Title: " + rca.issueTitle() + "\n" +
                 "Anomaly Type: " + rca.anomalyType() + "\n" +
                 "Root Cause: " + rca.rootCause() + "\n" +
                 "LLM Confidence (RCA): " + (rca.confidenceSummary() != null ? rca.confidenceSummary().rcaConfidenceScore() : null) + "\n" +
                 "LLM Confidence (Solution): " + (rca.confidenceSummary() != null ? rca.confidenceSummary().rcaConfidenceScore() : null) + "\n" +
                 "Recommendations: " + (rca.recommendations() != null ? rca.recommendations().size() : 0) + "\n" +
                 separator)
            .log();

        // Log diagnostic context length
        log.info("📋 Diagnostic Context (MCP raw data): " + diagnosticContext.length() + " chars")
            .log();

        // Log the full MCP diagnostic context for debugging
        String ctxSeparator = "=".repeat(80);
        log.info("\n" + ctxSeparator + "\n" +
                 "📋 FULL MCP DIAGNOSTIC CONTEXT (for assertion validation)\n" +
                 ctxSeparator + "\n" +
                 diagnosticContext + "\n" +
                 ctxSeparator)
            .log();

        Instant startTime = Instant.now();

        // ===== PATH A: Assertion-Based Validation =====
        log.info("PATH A: Starting assertion-based validation")
            .log();

        // Step 1: Extract assertions from RCA (capped at 15 to control cost/latency)
        List<Assertion> allAssertions = assertionExtractor.extractAssertions(rca);
        List<Assertion> assertions = allAssertions.size() > ValidationConstants.AssertionAnalysis.MAX_ASSERTIONS_PER_VALIDATION
            ? allAssertions.subList(0, ValidationConstants.AssertionAnalysis.MAX_ASSERTIONS_PER_VALIDATION)
            : allAssertions;

        log.info("✅ Assertions extracted: " + allAssertions.size() + " (using " + assertions.size() + ")")
            .log();

        // Log each assertion with full details
        log.info("\n" + "─".repeat(80) + "\n" +
                 "Extracted Assertions (Claims to Validate)\n" +
                 "─".repeat(80))
            .log();

        for (int i = 0; i < assertions.size(); i++) {
            Assertion a = assertions.get(i);
            log.info(String.format("  [%d] %s: %s | Source: %s",
                    i+1,
                    a.type(),
                    a.text(),
                    a.source()))
                .field("assertionId", a.id())
                .log();
        }

        log.info("─".repeat(80))
            .log();

        // Step 2: Validate assertions (LLM or Evidence Matcher)
        List<ValidationResult> assertionValidationResults;

        if (assertionAnalyzer.isPresent()) {
            log.info("Using LLM assertion analyzer")
                .field("assertionCount", assertions.size())
                .log();

            assertionValidationResults = assertionAnalyzer.get().analyzeAll(assertions, diagnosticContext);

        } else {
            log.info("Using rule-based evidence matcher")
                .field("assertionCount", assertions.size())
                .log();

            Map<String, List<Evidence>> evidenceMap = evidenceMatcher.findEvidenceForAll(
                assertions,
                diagnosticContext
            );

            assertionValidationResults = new ArrayList<>();
            for (Assertion assertion : assertions) {
                List<Evidence> evidence = evidenceMap.getOrDefault(assertion.id(), List.of());
                ValidationResult result = validateAssertion(assertion, evidence, minimumConfidence);
                assertionValidationResults.add(result);
            }
        }

        // Log each assertion validation result
        log.info("\n" + "─".repeat(80) + "\n" +
                 "PATH A: Assertion Validation Results\n" +
                 "─".repeat(80))
            .log();

        for (int i = 0; i < assertionValidationResults.size(); i++) {
            ValidationResult result = assertionValidationResults.get(i);
            String statusIcon = switch(result.status()) {
                case SUPPORTED -> "✅";
                case PARTIALLY_SUPPORTED -> "🟡";
                case UNSUPPORTED -> "❌";
                case UNKNOWN -> "❓";
            };

            log.info(String.format("  [%d] %s %s | Status: %s, Confidence: %.2f, Evidence: %d supporting",
                    i+1,
                    statusIcon,
                    result.assertion().text(),
                    result.status(),
                    result.confidence(),
                    result.supportingEvidence().size()))
                .field("assertionId", result.assertion().id())
                .log();
        }

        log.info("\n" + separator + "\n" +
                 "✅ PATH A COMPLETED\n" +
                 separator)
            .field("assertionResultsCount", assertionValidationResults.size())
            .log();

        // Log PATH A results summary
        long supported = assertionValidationResults.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.SUPPORTED)
            .count();
        long partial = assertionValidationResults.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED)
            .count();
        long unsupported = assertionValidationResults.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.UNSUPPORTED)
            .count();
        long unknown = assertionValidationResults.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.UNKNOWN)
            .count();

        log.info("PATH A Results: ✅ Supported=" + supported + ", 🟡 Partial=" + partial +
                 ", ❌ Unsupported=" + unsupported + ", ❓ Unknown=" + unknown)
            .log();

        // ===== PATH B: Rule-Based Hypothesis Validation =====
        HypothesisValidationResult ruleBasedResult = null;

        if (hypothesisValidator.isPresent()) {
            log.info("PATH B: Starting rule-based hypothesis validation")
                .log();

            ruleBasedResult = hypothesisValidator.get().validateHypothesis(rca, diagnosticContext);

            log.info("\n" + separator + "\n" +
                     "✅ PATH B COMPLETED\n" +
                     separator)
                .field("hypothesis", ruleBasedResult.getHypothesis())
                .field("status", ruleBasedResult.getStatus())
                .field("confidence", ruleBasedResult.getConfidence())
                .field("totalScore", ruleBasedResult.getTotalScore())
                .field("requiredRulesPassed", ruleBasedResult.getRequiredPassed())
                .field("supportingRulesMatched", ruleBasedResult.getSupportingMatched())
                .log();
        } else {
            log.info("PATH B: Skipped (hypothesis validator not available)")
                .log();
        }

        // ===== FINAL: Aggregate Both Paths =====
        DualValidationResult dualValidation = null;

        if (ruleBasedResult != null) {
            log.info("Aggregating dual validation results")
                .field("strategy", DEFAULT_STRATEGY)
                .log();

            dualValidation = validationAggregator.aggregate(
                assertionValidationResults,
                ruleBasedResult,
                DEFAULT_STRATEGY
            );

            log.info("\n" + separator + "\n" +
                     "🎯 DUAL VALIDATION AGGREGATION COMPLETED\n" +
                     separator + "\n" +
                     "Final Status: " + dualValidation.finalVerdict().status() + "\n" +
                     "Final Confidence: " + String.format("%.2f", dualValidation.finalVerdict().confidence()) + "\n" +
                     "PATH A Confidence: " + String.format("%.2f", dualValidation.assertionBasedVerdict().confidence()) + "\n" +
                     "PATH B Confidence: " + String.format("%.2f", dualValidation.ruleBasedVerdict().getConfidence()) + "\n" +
                     separator)
                .log();
        }

        // Step 3: Build validated RCA
        ValidatedRCA validatedRCA = ValidatedRCA.builder()
            .originalRca(rca)
            .validationResults(assertionValidationResults)
            .dualValidation(dualValidation)
            .validatedAt(startTime)
            .build();

        String finalSeparator = "=".repeat(80);
        log.info("\n" + finalSeparator + "\n" +
                 "✅ RCA VALIDATION PIPELINE COMPLETED\n" +
                 finalSeparator + "\n" +
                 "Issue: " + rca.issueTitle() + "\n" +
                 "Validation Summary: " + validatedRCA.summary().toSummaryString() + "\n" +
                 "Dual Validation: " + (dualValidation != null ? dualValidation.toSummaryString() : "N/A") + "\n" +
                 "Is Valid: " + validatedRCA.isValid() + "\n" +
                 "Is High Confidence: " + validatedRCA.isHighConfidence() + "\n" +
                 finalSeparator)
            .log();

        return validatedRCA;
    }

    /**
     * Validates a single assertion against evidence.
     */
    private ValidationResult validateAssertion(
        Assertion assertion,
        List<Evidence> evidence,
        double minimumConfidence
    ) {
        log.debug("Validating assertion")
            .field("assertionId", assertion.id())
            .field("assertionType", assertion.type())
            .field("evidenceCount", evidence.size())
            .log();

        // Recommendations don't need validation - they are suggestions
        if (assertion.type() == Assertion.AssertionType.RECOMMENDATION) {
            return ValidationResult.unknown(assertion, "Recommendations are not validated");
        }

        // No evidence found
        if (evidence.isEmpty()) {
            return ValidationResult.unknown(assertion, "No evidence found in diagnostic context");
        }

        // Separate supporting vs refuting evidence
        List<Evidence> supportingEvidence = new ArrayList<>();
        List<Evidence> refutingEvidence = new ArrayList<>();

        // For now, all found evidence is considered supporting
        // Future: Implement contradiction detection
        supportingEvidence.addAll(evidence);

        // Calculate confidence based on evidence strength
        double confidence = calculateConfidence(supportingEvidence, new ArrayList<>());

        // Determine validation status
        ValidationResult.ValidationStatus status = determineStatus(
            confidence,
            supportingEvidence.size(),
            0
        );

        return new ValidationResult(
            assertion,
            status,
            confidence,
            supportingEvidence,
            refutingEvidence,
            Optional.empty()
        );
    }

    /**
     * Calculates confidence score based on evidence.
     */
    private double calculateConfidence(
        List<Evidence> supporting,
        List<Evidence> refuting
    ) {
        if (supporting.isEmpty() && refuting.isEmpty()) {
            return 0.0;
        }

        // Calculate weighted support score
        double supportScore = supporting.stream()
            .mapToDouble(Evidence::relevanceScore)
            .average()
            .orElse(0.0);

        // Calculate weighted refute score
        double refuteScore = refuting.stream()
            .mapToDouble(Evidence::relevanceScore)
            .average()
            .orElse(0.0);

        // Boost confidence based on evidence count
        int totalEvidence = supporting.size() + refuting.size();
        double countBoost = Math.min(0.15, totalEvidence * 0.05);

        // Net confidence
        double confidence = supportScore - refuteScore + countBoost;

        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Determines validation status based on confidence and evidence counts.
     */
    private ValidationResult.ValidationStatus determineStatus(
        double confidence,
        int supportingCount,
        int refutingCount
    ) {
        // Strong support
        if (confidence >= STRONG_SUPPORT_THRESHOLD && supportingCount > 0) {
            return ValidationResult.ValidationStatus.SUPPORTED;
        }

        // Mixed evidence
        if (supportingCount > 0 && refutingCount > 0) {
            return ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED;
        }

        // Weak support
        if (confidence >= WEAK_SUPPORT_THRESHOLD && supportingCount > 0) {
            return ValidationResult.ValidationStatus.SUPPORTED;
        }

        // Strong refutation
        if (refutingCount > supportingCount) {
            return ValidationResult.ValidationStatus.UNSUPPORTED;
        }

        // Unknown
        return ValidationResult.ValidationStatus.UNKNOWN;
    }

    /**
     * Generates human-readable explanation of validation.
     */
    private String generateExplanation(
        ValidationResult.ValidationStatus status,
        double confidence,
        int supportingCount,
        int refutingCount
    ) {
        return switch (status) {
            case SUPPORTED -> String.format(
                "Assertion supported by %d evidence piece%s with %.0f%% confidence",
                supportingCount,
                supportingCount == 1 ? "" : "s",
                confidence * 100
            );
            case PARTIALLY_SUPPORTED -> String.format(
                "Assertion partially supported: %d supporting, %d refuting evidence (%.0f%% confidence)",
                supportingCount,
                refutingCount,
                confidence * 100
            );
            case UNSUPPORTED -> String.format(
                "Assertion contradicted by %d evidence piece%s",
                refutingCount,
                refutingCount == 1 ? "" : "s"
            );
            case UNKNOWN -> "Insufficient evidence to validate assertion";
        };
    }

}
