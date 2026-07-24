package com.causa.core.domain.validation;

import com.causa.common.constants.ValidationConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result of validating a single assertion against diagnostic context.
 *
 * <p>Contains the validation status, confidence score, supporting/refuting evidence,
 * and an explanation of the validation outcome.
 *
 * @since 0.0.1
 */
public record ValidationResult(
    Assertion assertion,
    ValidationStatus status,
    double confidence,
    List<Evidence> supportingEvidence,
    List<Evidence> refutingEvidence,
    Optional<String> explanation
) {

    /**
     * Creates a new validation result.
     *
     * @param assertion the assertion that was validated
     * @param status the validation status
     * @param confidence confidence in the validation (0.0 to 1.0)
     * @param supportingEvidence evidence that supports the assertion
     * @param refutingEvidence evidence that refutes the assertion
     * @param explanation optional explanation of the validation reasoning
     */
    public ValidationResult {
        if (assertion == null) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.VALIDATION_ASSERTION_NULL);
        }
        if (status == null) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.VALIDATION_STATUS_NULL);
        }
        if (confidence < ValidationConstants.Thresholds.MIN_CONFIDENCE ||
            confidence > ValidationConstants.Thresholds.MAX_CONFIDENCE) {
            throw new IllegalArgumentException(ValidationConstants.ErrorMessages.VALIDATION_CONFIDENCE_RANGE);
        }
        if (supportingEvidence == null) {
            supportingEvidence = Collections.emptyList();
        } else {
            supportingEvidence = Collections.unmodifiableList(new ArrayList<>(supportingEvidence));
        }
        if (refutingEvidence == null) {
            refutingEvidence = Collections.emptyList();
        } else {
            refutingEvidence = Collections.unmodifiableList(new ArrayList<>(refutingEvidence));
        }
        if (explanation == null) {
            explanation = Optional.empty();
        }
    }

    /**
     * Creates a supported validation result.
     */
    public static ValidationResult supported(Assertion assertion, double confidence, List<Evidence> evidence, String explanation) {
        return new ValidationResult(
            assertion,
            ValidationStatus.SUPPORTED,
            confidence,
            evidence,
            Collections.emptyList(),
            Optional.of(explanation)
        );
    }

    /**
     * Creates a partially supported validation result.
     */
    public static ValidationResult partiallySupported(
        Assertion assertion,
        double confidence,
        List<Evidence> supportingEvidence,
        List<Evidence> refutingEvidence,
        String explanation
    ) {
        return new ValidationResult(
            assertion,
            ValidationStatus.PARTIALLY_SUPPORTED,
            confidence,
            supportingEvidence,
            refutingEvidence,
            Optional.of(explanation)
        );
    }

    /**
     * Creates an unsupported validation result.
     */
    public static ValidationResult unsupported(Assertion assertion, double confidence, List<Evidence> evidence, String explanation) {
        return new ValidationResult(
            assertion,
            ValidationStatus.UNSUPPORTED,
            confidence,
            Collections.emptyList(),
            evidence,
            Optional.of(explanation)
        );
    }

    /**
     * Creates an unknown validation result (insufficient evidence).
     */
    public static ValidationResult unknown(Assertion assertion, String explanation) {
        return new ValidationResult(
            assertion,
            ValidationStatus.UNKNOWN,
            0.0,
            Collections.emptyList(),
            Collections.emptyList(),
            Optional.of(explanation)
        );
    }

    /**
     * Returns true if this assertion was validated (supported or partially supported).
     */
    public boolean isValidated() {
        return status == ValidationStatus.SUPPORTED || status == ValidationStatus.PARTIALLY_SUPPORTED;
    }

    /**
     * Returns true if strong evidence was found (high confidence).
     */
    public boolean hasStrongEvidence() {
        return confidence >= ValidationConstants.Thresholds.HIGH_CONFIDENCE_THRESHOLD;
    }

    /**
     * Returns the total number of evidence pieces.
     */
    public int evidenceCount() {
        return supportingEvidence.size() + refutingEvidence.size();
    }

    /**
     * Status of assertion validation.
     */
    public enum ValidationStatus {
        /** Assertion is supported by strong evidence */
        SUPPORTED,

        /** Assertion is partially supported (mixed evidence) */
        PARTIALLY_SUPPORTED,

        /** Assertion is contradicted by evidence */
        UNSUPPORTED,

        /** Insufficient evidence to validate */
        UNKNOWN
    }

    /**
     * Builder for creating validation results.
     */
    public static class Builder {
        private Assertion assertion;
        private ValidationStatus status;
        private double confidence;
        private List<Evidence> supportingEvidence = new ArrayList<>();
        private List<Evidence> refutingEvidence = new ArrayList<>();
        private String explanation;

        public Builder assertion(Assertion assertion) {
            this.assertion = assertion;
            return this;
        }

        public Builder status(ValidationStatus status) {
            this.status = status;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder addSupportingEvidence(Evidence evidence) {
            this.supportingEvidence.add(evidence);
            return this;
        }

        public Builder addRefutingEvidence(Evidence evidence) {
            this.refutingEvidence.add(evidence);
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = explanation;
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(
                assertion,
                status,
                confidence,
                supportingEvidence,
                refutingEvidence,
                Optional.ofNullable(explanation)
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
