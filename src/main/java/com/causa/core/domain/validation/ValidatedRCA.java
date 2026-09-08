package com.causa.core.domain.validation;

import com.causa.common.constants.ValidationConstants;
import com.causa.core.domain.RootCauseAnalysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Root Cause Analysis with validation results.
 *
 * <p>Wraps the original RCA output with:
 * <ul>
 *   <li>Assertion-level validation results (PATH A)</li>
 *   <li>Rule-based hypothesis validation (PATH B)</li>
 *   <li>Dual validation aggregation (FINAL VERDICT)</li>
 *   <li>Overall confidence scores and quality metrics</li>
 * </ul>
 *
 * @since 0.0.1
 */
public record ValidatedRCA(
    RootCauseAnalysis originalRca,
    List<ValidationResult> validationResults,
    ValidationSummary summary,
    DualValidationResult dualValidation,
    Instant validatedAt
) {

    /**
     * Creates a validated RCA instance.
     *
     * @param originalRca the original RCA from the LLM
     * @param validationResults validation results for each assertion (PATH A)
     * @param summary validation summary metrics
     * @param dualValidation dual validation result (PATH A + PATH B aggregated)
     * @param validatedAt timestamp when validation was performed
     */
    public ValidatedRCA {
        if (originalRca == null) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.VALIDATED_RCA_ORIGINAL_NULL);
        }
        if (validationResults == null) {
            validationResults = Collections.emptyList();
        } else {
            validationResults = Collections.unmodifiableList(new ArrayList<>(validationResults));
        }
        if (summary == null) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.VALIDATED_RCA_SUMMARY_NULL);
        }
        // dualValidation can be null for backward compatibility
        if (validatedAt == null) {
            validatedAt = Instant.now();
        }
    }

    /**
     * Returns true if the RCA passed validation.
     *
     * <p>Uses dual validation final verdict if available, otherwise falls back to assertion-based score.
     */
    public boolean isValid() {
        if (dualValidation != null) {
            return dualValidation.finalVerdict().status() == ValidationResult.ValidationStatus.SUPPORTED;
        }
        return summary.validationScore() >= 0.7; // 70% threshold (backward compatibility)
    }

    /**
     * Returns true if high confidence validation.
     *
     * <p>Uses dual validation final confidence if available, otherwise falls back to assertion average.
     */
    public boolean isHighConfidence() {
        if (dualValidation != null) {
            return dualValidation.finalVerdict().confidence() >= ValidationConstants.Thresholds.HIGH_CONFIDENCE_THRESHOLD;
        }
        return summary.averageConfidence() >= ValidationConstants.Thresholds.HIGH_CONFIDENCE_THRESHOLD; // Backward compatibility
    }

    /**
     * Gets all supported assertions.
     */
    public List<ValidationResult> getSupportedAssertions() {
        return validationResults.stream()
            .filter(vr -> vr.status() == ValidationResult.ValidationStatus.SUPPORTED)
            .collect(Collectors.toList());
    }

    /**
     * Gets all unsupported assertions.
     */
    public List<ValidationResult> getUnsupportedAssertions() {
        return validationResults.stream()
            .filter(vr -> vr.status() == ValidationResult.ValidationStatus.UNSUPPORTED)
            .collect(Collectors.toList());
    }

    /**
     * Gets assertions with insufficient evidence.
     */
    public List<ValidationResult> getUnknownAssertions() {
        return validationResults.stream()
            .filter(vr -> vr.status() == ValidationResult.ValidationStatus.UNKNOWN)
            .collect(Collectors.toList());
    }

    /**
     * Summary of validation results.
     */
    public record ValidationSummary(
        int totalAssertions,
        int supportedCount,
        int partiallySupportedCount,
        int unsupportedCount,
        int unknownCount,
        double validationScore,
        double averageConfidence,
        int totalEvidencePieces
    ) {

        /**
         * Creates a validation summary from validation results.
         */
        public static ValidationSummary from(List<ValidationResult> results) {
            int total = results.size();
            int supported = (int) results.stream()
                .filter(vr -> vr.status() == ValidationResult.ValidationStatus.SUPPORTED)
                .count();
            int partiallySupported = (int) results.stream()
                .filter(vr -> vr.status() == ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED)
                .count();
            int unsupported = (int) results.stream()
                .filter(vr -> vr.status() == ValidationResult.ValidationStatus.UNSUPPORTED)
                .count();
            int unknown = (int) results.stream()
                .filter(vr -> vr.status() == ValidationResult.ValidationStatus.UNKNOWN)
                .count();

            // Validation score: (supported + 0.5 * partiallySupported) / total
            double validationScore = total > 0
                ? (supported + 0.5 * partiallySupported) / (double) total
                : 0.0;

            // Average confidence across all assertions
            double avgConfidence = results.stream()
                .mapToDouble(ValidationResult::confidence)
                .average()
                .orElse(0.0);

            // Total evidence pieces
            int totalEvidence = results.stream()
                .mapToInt(ValidationResult::evidenceCount)
                .sum();

            return new ValidationSummary(
                total,
                supported,
                partiallySupported,
                unsupported,
                unknown,
                validationScore,
                avgConfidence,
                totalEvidence
            );
        }

        /**
         * Returns a human-readable summary.
         */
        public String toSummaryString() {
            return String.format(
                "Validated %d assertions: %d supported, %d partial, %d unsupported, %d unknown | " +
                "Score: %.2f | Avg Confidence: %.2f | Evidence: %d pieces",
                totalAssertions,
                supportedCount,
                partiallySupportedCount,
                unsupportedCount,
                unknownCount,
                validationScore,
                averageConfidence,
                totalEvidencePieces
            );
        }
    }

    /**
     * Builder for creating validated RCA.
     */
    public static class Builder {
        private RootCauseAnalysis originalRca;
        private List<ValidationResult> validationResults = new ArrayList<>();
        private DualValidationResult dualValidation;
        private Instant validatedAt = Instant.now();

        public Builder originalRca(RootCauseAnalysis rca) {
            this.originalRca = rca;
            return this;
        }

        public Builder addValidationResult(ValidationResult result) {
            this.validationResults.add(result);
            return this;
        }

        public Builder validationResults(List<ValidationResult> results) {
            this.validationResults = new ArrayList<>(results);
            return this;
        }

        public Builder dualValidation(DualValidationResult dualValidation) {
            this.dualValidation = dualValidation;
            return this;
        }

        public Builder validatedAt(Instant timestamp) {
            this.validatedAt = timestamp;
            return this;
        }

        public ValidatedRCA build() {
            ValidationSummary summary = ValidationSummary.from(validationResults);
            return new ValidatedRCA(
                originalRca,
                validationResults,
                summary,
                dualValidation,
                validatedAt
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
