package com.causa.core.domain.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating a single assertion against diagnostic context.
 *
 * <p>Contains the validation status, confidence score, and supporting evidence.
 *
 * @since 0.0.1
 */
public record ValidationResult(
    Assertion assertion,
    ValidationStatus status,
    double confidence,
    List<Evidence> supportingEvidence
) {

    /**
     * Creates a new validation result.
     *
     * @param assertion the assertion that was validated
     * @param status the validation status
     * @param confidence confidence in the validation (0.0 to 1.0)
     * @param supportingEvidence evidence that supports the assertion
     */
    public ValidationResult {
        if (assertion == null) {
            throw new IllegalArgumentException("Assertion cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Validation status cannot be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        if (supportingEvidence == null) {
            supportingEvidence = Collections.emptyList();
        } else {
            supportingEvidence = Collections.unmodifiableList(new ArrayList<>(supportingEvidence));
        }
    }

    /**
     * Creates a supported validation result.
     */
    public static ValidationResult supported(Assertion assertion, double confidence, List<Evidence> evidence) {
        return new ValidationResult(
            assertion,
            ValidationStatus.SUPPORTED,
            confidence,
            evidence
        );
    }

    /**
     * Creates a partially supported validation result.
     */
    public static ValidationResult partiallySupported(Assertion assertion, double confidence, List<Evidence> supportingEvidence) {
        return new ValidationResult(
            assertion,
            ValidationStatus.PARTIALLY_SUPPORTED,
            confidence,
            supportingEvidence
        );
    }

    /**
     * Creates an unsupported validation result.
     */
    public static ValidationResult unsupported(Assertion assertion, double confidence) {
        return new ValidationResult(
            assertion,
            ValidationStatus.UNSUPPORTED,
            confidence,
            Collections.emptyList()
        );
    }

    /**
     * Creates an unknown validation result (insufficient evidence).
     */
    public static ValidationResult unknown(Assertion assertion) {
        return new ValidationResult(
            assertion,
            ValidationStatus.UNKNOWN,
            0.0,
            Collections.emptyList()
        );
    }

    /**
     * Returns true if strong evidence was found (high confidence).
     */
    public boolean hasStrongEvidence() {
        return confidence >= 0.8;
    }

    /**
     * Returns the total number of evidence pieces.
     */
    public int evidenceCount() {
        return supportingEvidence.size();
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

        public ValidationResult build() {
            return new ValidationResult(
                assertion,
                status,
                confidence,
                supportingEvidence
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
