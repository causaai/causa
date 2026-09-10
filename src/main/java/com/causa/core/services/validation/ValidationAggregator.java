package com.causa.core.services.validation;

import com.causa.common.constants.ValidationConstants.Aggregation;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.validation.DualValidationResult;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.rules.HypothesisValidationResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Validation Aggregator.
 *
 * <p>Aggregates results from dual validation paths:
 * <ol>
 *   <li><strong>PATH A:</strong> Aggregate assertion validation results → verdict</li>
 *   <li><strong>PATH B:</strong> Rule-based hypothesis validation → verdict</li>
 *   <li><strong>FINAL:</strong> Combine both verdicts → final RCA validation</li>
 * </ol>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ValidationAggregator {

    private static final CausaLogger log = CausaLogger.getLogger(ValidationAggregator.class);

    /**
     * Aggregate dual validation results into final verdict.
     *
     * @param assertionResults   list of assertion validation results (PATH A)
     * @param ruleBasedResult    hypothesis validation result (PATH B)
     * @param strategy           aggregation strategy
     * @return dual validation result with final verdict
     */
    public DualValidationResult aggregate(
        List<ValidationResult> assertionResults,
        HypothesisValidationResult ruleBasedResult,
        DualValidationResult.FinalVerdict.AggregationStrategy strategy
    ) {
        log.info(LogMessages.Validation.HYPOTHESIS_VALIDATION_STARTED)
            .field("assertionCount", assertionResults.size())
            .field("ruleBasedStatus", ruleBasedResult.getStatus())
            .field("strategy", strategy)
            .log();

        // Step 1: Aggregate assertion results (PATH A)
        DualValidationResult.AssertionBasedVerdict assertionVerdict =
            aggregateAssertionResults(assertionResults);

        // Step 2: Combine both verdicts (PATH A + PATH B)
        DualValidationResult.FinalVerdict finalVerdict =
            combineVerdicts(assertionVerdict, ruleBasedResult, strategy);

        DualValidationResult result = new DualValidationResult(
            assertionVerdict,
            ruleBasedResult,
            finalVerdict
        );

        log.info(LogMessages.Validation.HYPOTHESIS_VALIDATION_COMPLETED)
            .field("finalStatus", finalVerdict.status())
            .field("finalConfidence", finalVerdict.confidence())
            .field("summary", result.toSummaryString())
            .log();

        return result;
    }

    /**
     * Aggregate individual assertion validation results into overall verdict (PATH A).
     */
    private DualValidationResult.AssertionBasedVerdict aggregateAssertionResults(
        List<ValidationResult> results
    ) {
        if (results.isEmpty()) {
            return new DualValidationResult.AssertionBasedVerdict(
                ValidationResult.ValidationStatus.UNKNOWN,
                0.0,
                0, 0, 0, 0, 0,
                Aggregation.NO_ASSERTIONS
            );
        }

        int total = results.size();
        int supported = (int) results.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.SUPPORTED)
            .count();
        int partiallySupported = (int) results.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED)
            .count();
        int unsupported = (int) results.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.UNSUPPORTED)
            .count();
        int unknown = (int) results.stream()
            .filter(r -> r.status() == ValidationResult.ValidationStatus.UNKNOWN)
            .count();

        double avgConfidence = results.stream()
            .mapToDouble(ValidationResult::confidence)
            .average()
            .orElse(0.0);

        ValidationResult.ValidationStatus status = determineAssertionStatus(
            total, supported, partiallySupported, unsupported, unknown
        );

        String explanation = String.format(
            "Assertions: %d supported, %d partial, %d unsupported, %d unknown out of %d total",
            supported, partiallySupported, unsupported, unknown, total
        );

        return new DualValidationResult.AssertionBasedVerdict(
            status,
            avgConfidence,
            total,
            supported,
            partiallySupported,
            unsupported,
            unknown,
            explanation
        );
    }

    /**
     * Determine overall assertion validation status.
     */
    private ValidationResult.ValidationStatus determineAssertionStatus(
        int total, int supported, int partiallySupported, int unsupported, int unknown
    ) {
        double supportedRatio = (double) supported / total;
        double unsupportedRatio = (double) unsupported / total;

        if (supportedRatio >= Aggregation.SUPPORTED_RATIO_THRESHOLD) {
            return ValidationResult.ValidationStatus.SUPPORTED;
        }

        if (unsupportedRatio >= Aggregation.UNSUPPORTED_RATIO_THRESHOLD) {
            return ValidationResult.ValidationStatus.UNSUPPORTED;
        }

        if (supportedRatio + (partiallySupported * Aggregation.PARTIAL_SUPPORT_WEIGHT_FACTOR / total)
                >= Aggregation.PARTIAL_SUPPORT_RATIO_THRESHOLD) {
            return ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED;
        }

        return ValidationResult.ValidationStatus.UNSUPPORTED;
    }

    /**
     * Combine assertion verdict and rule-based verdict into final verdict.
     */
    private DualValidationResult.FinalVerdict combineVerdicts(
        DualValidationResult.AssertionBasedVerdict assertionVerdict,
        HypothesisValidationResult ruleBasedVerdict,
        DualValidationResult.FinalVerdict.AggregationStrategy strategy
    ) {
        return switch (strategy) {
            case STRICT_CONSENSUS -> strictConsensus(assertionVerdict, ruleBasedVerdict);
            case HIGHEST_CONFIDENCE -> highestConfidence(assertionVerdict, ruleBasedVerdict);
            case WEIGHTED_AVERAGE -> weightedAverage(assertionVerdict, ruleBasedVerdict);
            case RULE_BASED_PRIORITY -> ruleBasedPriority(assertionVerdict, ruleBasedVerdict);
        };
    }

    /**
     * STRICT_CONSENSUS: Both paths must agree (conservative).
     */
    private DualValidationResult.FinalVerdict strictConsensus(
        DualValidationResult.AssertionBasedVerdict assertionVerdict,
        HypothesisValidationResult ruleBasedVerdict
    ) {
        ValidationResult.ValidationStatus assertionStatus = assertionVerdict.status();
        ValidationResult.ValidationStatus ruleStatus = mapRuleStatus(ruleBasedVerdict.getStatus());

        if (assertionStatus == ValidationResult.ValidationStatus.SUPPORTED &&
            ruleStatus == ValidationResult.ValidationStatus.SUPPORTED) {

            double confidence = Math.min(assertionVerdict.confidence(), ruleBasedVerdict.getConfidence());
            return new DualValidationResult.FinalVerdict(
                ValidationResult.ValidationStatus.SUPPORTED,
                confidence,
                DualValidationResult.FinalVerdict.AggregationStrategy.STRICT_CONSENSUS,
                Aggregation.BOTH_SUPPORT
            );
        }

        if (assertionStatus == ValidationResult.ValidationStatus.UNSUPPORTED &&
            ruleStatus == ValidationResult.ValidationStatus.UNSUPPORTED) {

            double confidence = Math.max(assertionVerdict.confidence(), ruleBasedVerdict.getConfidence());
            return new DualValidationResult.FinalVerdict(
                ValidationResult.ValidationStatus.UNSUPPORTED,
                confidence,
                DualValidationResult.FinalVerdict.AggregationStrategy.STRICT_CONSENSUS,
                Aggregation.BOTH_REJECT
            );
        }

        double avgConfidence = (assertionVerdict.confidence() + ruleBasedVerdict.getConfidence()) / 2.0;
        return new DualValidationResult.FinalVerdict(
            ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED,
            avgConfidence,
            DualValidationResult.FinalVerdict.AggregationStrategy.STRICT_CONSENSUS,
            String.format("Paths disagree: assertions=%s, rules=%s", assertionStatus, ruleStatus)
        );
    }

    /**
     * HIGHEST_CONFIDENCE: Use the path with higher confidence.
     */
    private DualValidationResult.FinalVerdict highestConfidence(
        DualValidationResult.AssertionBasedVerdict assertionVerdict,
        HypothesisValidationResult ruleBasedVerdict
    ) {
        boolean assertionWins = assertionVerdict.confidence() >= ruleBasedVerdict.getConfidence();

        if (assertionWins) {
            return new DualValidationResult.FinalVerdict(
                assertionVerdict.status(),
                assertionVerdict.confidence(),
                DualValidationResult.FinalVerdict.AggregationStrategy.HIGHEST_CONFIDENCE,
                Aggregation.ASSERTION_HIGHER_CONFIDENCE
            );
        } else {
            return new DualValidationResult.FinalVerdict(
                mapRuleStatus(ruleBasedVerdict.getStatus()),
                ruleBasedVerdict.getConfidence(),
                DualValidationResult.FinalVerdict.AggregationStrategy.HIGHEST_CONFIDENCE,
                Aggregation.RULE_HIGHER_CONFIDENCE
            );
        }
    }

    /**
     * WEIGHTED_AVERAGE: Average both paths with configurable weights.
     *
     * <p>Default weights:
     * <ul>
     *   <li>PATH A (Assertion-based LLM): 40% - more nuanced but can be uncertain</li>
     *   <li>PATH B (Rule-based deterministic): 60% - faster, more reliable for known patterns</li>
     * </ul>
     */
    private DualValidationResult.FinalVerdict weightedAverage(
        DualValidationResult.AssertionBasedVerdict assertionVerdict,
        HypothesisValidationResult ruleBasedVerdict
    ) {
        double assertionScore = statusToScore(assertionVerdict.status());
        double ruleScore = statusToScore(mapRuleStatus(ruleBasedVerdict.getStatus()));

        double combinedScore = (assertionScore * Aggregation.ASSERTION_WEIGHT) +
                               (ruleScore * Aggregation.RULE_WEIGHT);

        double avgConfidence = (assertionVerdict.confidence() * Aggregation.ASSERTION_WEIGHT) +
                               (ruleBasedVerdict.getConfidence() * Aggregation.RULE_WEIGHT);

        ValidationResult.ValidationStatus finalStatus = scoreToStatus(combinedScore);

        return new DualValidationResult.FinalVerdict(
            finalStatus,
            avgConfidence,
            DualValidationResult.FinalVerdict.AggregationStrategy.WEIGHTED_AVERAGE,
            String.format("Weighted average: assertion=%.2f (weight=%.1f), rule=%.2f (weight=%.1f), final=%.2f",
                assertionScore, Aggregation.ASSERTION_WEIGHT, ruleScore, Aggregation.RULE_WEIGHT, combinedScore)
        );
    }

    /**
     * RULE_BASED_PRIORITY: Deterministic rules take priority over LLM.
     */
    private DualValidationResult.FinalVerdict ruleBasedPriority(
        DualValidationResult.AssertionBasedVerdict assertionVerdict,
        HypothesisValidationResult ruleBasedVerdict
    ) {
        if (ruleBasedVerdict.getStatus() == HypothesisValidationResult.ValidationStatus.SUPPORTED ||
            ruleBasedVerdict.getStatus() == HypothesisValidationResult.ValidationStatus.UNSUPPORTED) {

            return new DualValidationResult.FinalVerdict(
                mapRuleStatus(ruleBasedVerdict.getStatus()),
                ruleBasedVerdict.getConfidence(),
                DualValidationResult.FinalVerdict.AggregationStrategy.RULE_BASED_PRIORITY,
                Aggregation.RULE_DETERMINISTIC
            );
        }

        return new DualValidationResult.FinalVerdict(
            assertionVerdict.status(),
            assertionVerdict.confidence(),
            DualValidationResult.FinalVerdict.AggregationStrategy.RULE_BASED_PRIORITY,
            Aggregation.RULE_INCONCLUSIVE
        );
    }

    /**
     * Map HypothesisValidationResult.ValidationStatus to ValidationResult.ValidationStatus.
     */
    private ValidationResult.ValidationStatus mapRuleStatus(
        HypothesisValidationResult.ValidationStatus ruleStatus
    ) {
        return switch (ruleStatus) {
            case SUPPORTED -> ValidationResult.ValidationStatus.SUPPORTED;
            case PARTIALLY_SUPPORTED -> ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED;
            case UNSUPPORTED -> ValidationResult.ValidationStatus.UNSUPPORTED;
        };
    }

    /**
     * Convert status to numeric score for averaging.
     */
    private double statusToScore(ValidationResult.ValidationStatus status) {
        return switch (status) {
            case SUPPORTED -> Aggregation.SCORE_SUPPORTED;
            case PARTIALLY_SUPPORTED -> Aggregation.SCORE_PARTIALLY_SUPPORTED;
            case UNSUPPORTED -> Aggregation.SCORE_UNSUPPORTED;
            case UNKNOWN -> Aggregation.SCORE_UNKNOWN;
        };
    }

    /**
     * Convert numeric score back to status.
     */
    private ValidationResult.ValidationStatus scoreToStatus(double score) {
        if (score >= Aggregation.SUPPORTED_SCORE_THRESHOLD) {
            return ValidationResult.ValidationStatus.SUPPORTED;
        } else if (score >= Aggregation.PARTIALLY_SUPPORTED_SCORE_THRESHOLD) {
            return ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED;
        } else {
            return ValidationResult.ValidationStatus.UNSUPPORTED;
        }
    }
}
