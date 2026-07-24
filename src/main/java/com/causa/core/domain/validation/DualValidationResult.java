package com.causa.core.domain.validation;

import com.causa.common.constants.ValidationConstants;
import com.causa.core.services.rules.HypothesisValidationResult;

/**
 * Dual Validation Result.
 *
 * <p>Combines results from two parallel validation paths:
 * <ul>
 *   <li><strong>PATH A (Assertion-based):</strong> LLM validates individual assertions, aggregated into verdict</li>
 *   <li><strong>PATH B (Rule-based):</strong> Deterministic rules validate hypothesis directly</li>
 * </ul>
 *
 * <p>Final verdict is determined by aggregating both paths.
 *
 * @since 0.0.1
 */
public record DualValidationResult(
    AssertionBasedVerdict assertionBasedVerdict,
    HypothesisValidationResult ruleBasedVerdict,
    FinalVerdict finalVerdict
) {

    /**
     * Assertion-Based Verdict (PATH A aggregated).
     */
    public record AssertionBasedVerdict(
        ValidationResult.ValidationStatus status,
        double confidence,
        int totalAssertions,
        int supportedAssertions,
        int partiallySupportedAssertions,
        int unsupportedAssertions,
        int unknownAssertions,
        String explanation
    ) {
        /**
         * Calculate validation score (0.0 - 1.0).
         */
        public double validationScore() {
            if (totalAssertions == 0) {
                return ValidationConstants.Thresholds.MIN_CONFIDENCE;
            }
            double weightedScore = (supportedAssertions * ValidationConstants.ScoringWeights.SUPPORTED_WEIGHT) +
                                   (partiallySupportedAssertions * ValidationConstants.ScoringWeights.PARTIALLY_SUPPORTED_WEIGHT) +
                                   (unknownAssertions * ValidationConstants.ScoringWeights.UNKNOWN_WEIGHT) +
                                   (unsupportedAssertions * ValidationConstants.ScoringWeights.UNSUPPORTED_WEIGHT);
            return Math.max(
                ValidationConstants.Thresholds.MIN_CONFIDENCE,
                Math.min(ValidationConstants.Thresholds.MAX_CONFIDENCE, weightedScore / totalAssertions)
            );
        }
    }

    /**
     * Final Verdict (aggregated from both paths).
     */
    public record FinalVerdict(
        ValidationResult.ValidationStatus status,
        double confidence,
        AggregationStrategy strategy,
        String explanation
    ) {
        public enum AggregationStrategy {
            /**
             * Both paths must agree (conservative).
             */
            STRICT_CONSENSUS,

            /**
             * Use the more confident path.
             */
            HIGHEST_CONFIDENCE,

            /**
             * Weighted average of both paths.
             */
            WEIGHTED_AVERAGE,

            /**
             * Rule-based takes priority (deterministic wins).
             */
            RULE_BASED_PRIORITY
        }

        public boolean isSupported() {
            return status == ValidationResult.ValidationStatus.SUPPORTED;
        }

        public boolean isHighConfidence() {
            return confidence >= 0.8;
        }
    }

    /**
     * Check if RCA is validated (final verdict is SUPPORTED).
     */
    public boolean isValidated() {
        return finalVerdict.status() == ValidationResult.ValidationStatus.SUPPORTED;
    }

    /**
     * Check if validation has high confidence.
     */
    public boolean isHighConfidence() {
        return finalVerdict.confidence() >= ValidationConstants.Thresholds.HIGH_CONFIDENCE_THRESHOLD;
    }

    /**
     * Get summary string.
     */
    public String toSummaryString() {
        return String.format(
            "Final: %s (conf=%.2f) | Assertions: %s (conf=%.2f) | Rules: %s (conf=%.2f)",
            finalVerdict.status(),
            finalVerdict.confidence(),
            assertionBasedVerdict.status(),
            assertionBasedVerdict.confidence(),
            ruleBasedVerdict.getStatus(),
            ruleBasedVerdict.getConfidence()
        );
    }
}
