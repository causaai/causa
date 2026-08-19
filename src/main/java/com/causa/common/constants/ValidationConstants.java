package com.causa.common.constants;

/**
 * Validation Constants
 *
 * <p>Constants for RCA validation pipeline including error messages,
 * scoring weights, and validation thresholds.
 *
 * @since 0.0.1
 */
public final class ValidationConstants {

    private ValidationConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * Validation error messages
     */
    public static final class ErrorMessages {
        private ErrorMessages() {}

        // Assertion validation errors
        public static final String ASSERTION_ID_BLANK = "Assertion ID cannot be blank";
        public static final String ASSERTION_TEXT_BLANK = "Assertion text cannot be blank";
        public static final String ASSERTION_TYPE_NULL = "Assertion type cannot be null";
        public static final String ASSERTION_SOURCE_NULL = "Assertion source cannot be null";

        // Validation result errors
        public static final String VALIDATION_ASSERTION_NULL = "Assertion cannot be null";
        public static final String VALIDATION_STATUS_NULL = "Validation status cannot be null";
        public static final String VALIDATION_CONFIDENCE_RANGE = "Confidence must be between 0.0 and 1.0";

        // Evidence validation errors
        public static final String EVIDENCE_SOURCE_BLANK = "Evidence source cannot be blank";
        public static final String EVIDENCE_TYPE_NULL = "Evidence type cannot be null";
        public static final String EVIDENCE_SNIPPET_BLANK = "Evidence snippet cannot be blank";
        public static final String EVIDENCE_RELEVANCE_RANGE = "Relevance score must be between 0.0 and 1.0";

        // ValidatedRCA errors
        public static final String VALIDATED_RCA_ORIGINAL_NULL = "Original RCA cannot be null";
        public static final String VALIDATED_RCA_SUMMARY_NULL = "Validation summary cannot be null";
    }

    /**
     * Validation scoring weights
     */
    public static final class ScoringWeights {
        private ScoringWeights() {}

        // Assertion type weights for validation score calculation
        public static final double SUPPORTED_WEIGHT = 1.0;
        public static final double PARTIALLY_SUPPORTED_WEIGHT = 0.5;
        public static final double UNKNOWN_WEIGHT = 0.0;
        public static final double UNSUPPORTED_WEIGHT = -0.5;
    }

    /**
     * Validation thresholds
     */
    public static final class Thresholds {
        private Thresholds() {}

        public static final double MIN_CONFIDENCE = 0.0;
        public static final double MAX_CONFIDENCE = 1.0;
        public static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;
        public static final double MEDIUM_CONFIDENCE_THRESHOLD = 0.5;
    }

    /**
     * Validation log field names
     */
    public static final class LogFields {
        private LogFields() {}

        public static final String VALIDATION_RESULT = "validationResult";
        public static final String CONFIDENCE_SCORE = "confidenceScore";
        public static final String SUPPORTED_COUNT = "supportedCount";
        public static final String UNSUPPORTED_COUNT = "unsupportedCount";
        public static final String UNKNOWN_COUNT = "unknownCount";
    }
}
