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
     * Assertion analysis configuration
     */
    public static final class AssertionAnalysis {
        private AssertionAnalysis() {}

        public static final int MAX_ASSERTIONS_PER_VALIDATION = 15;
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

    /**
     * Signal names emitted by the signal extractor
     */
    public static final class SignalNames {
        private SignalNames() {}

        // Kubernetes event signals
        public static final String REASON = "reason";
        public static final String TERMINATION_REASON = "terminationReason";
        public static final String DEPLOYMENT = "deployment";
        public static final String EVICTION = "eviction";

        // Container status signals
        public static final String EXIT_CODE = "exitCode";

        // Pod status signals
        public static final String STATUS = "status";
        public static final String POD_STATE = "podState";

        // Memory metric signals
        public static final String MEMORY_UTILIZATION_TREND = "memory.utilization.trend";
        public static final String HEAP_USAGE_TREND = "heap.usage.trend";
        public static final String HEAP_USAGE = "heap.usage";
        public static final String MEMORY_PRESSURE_DURATION = "memory.pressure.duration";
        public static final String MEMORY_USAGE_PERCENT = "memory.usage.percent";

        // GC metric signals
        public static final String GC_PAUSE_MAX = "gc.pause.max";
        public static final String GC_PAUSE_TOTAL = "gc.pause.total";
        public static final String HEAP_AFTER_GC_RATIO = "heap.after.gc.ratio";

        // Log pattern signals
        public static final String ERROR_OOM = "error.oom";
        public static final String FULL_GC_COUNT = "full.gc.count";

        // Kruize signals
        public static final String MEMORY_LIMIT_RECOMMENDATION = "memory.limit.recommendation";
    }

    /**
     * Signal values used by the signal extractor
     */
    public static final class SignalValues {
        private SignalValues() {}

        public static final String OOM_KILLED = "OOMKilled";
        public static final String CRASH_LOOP_BACK_OFF = "CrashLoopBackOff";
        public static final String INCREASING = "INCREASING";
        public static final String DEPLOYMENT_ROLLOUT_DETECTED = "deployment rollout detected";
        public static final String INCREASE_MEMORY_LIMIT = "increase memory limit";
        public static final String DISK_PRESSURE = "disk pressure";
        public static final String MEMORY_PRESSURE = "memory pressure";
        public static final String EVICTION_PREFIX = "eviction due to ";
    }

    /**
     * Signal metadata keys and source values
     */
    public static final class SignalMetadata {
        private SignalMetadata() {}

        // Metadata keys
        public static final String SOURCE = "source";
        public static final String FREQUENT = "frequent";
        public static final String RECOMMENDATION = "recommendation";

        // Source values
        public static final String SOURCE_QUARKUS_AFTER_GC = "quarkus_jvm_memory_usage_after_gc";
        public static final String SOURCE_REMAINING_LOGS = "derived_from_remaining_logs";
        public static final String SOURCE_OOMKILLED_RESTARTS = "derived_from_oomkilled_with_restarts";
        public static final String SOURCE_RESTART_COUNT = "derived_from_restart_count";
        public static final String SOURCE_QUARKUS_MEMORY = "quarkus_jvm_memory";
        public static final String SOURCE_VERBOSE_GC = "verbose_gc_logs";
        public static final String SOURCE_GC_LOGS = "derived_from_gc_logs";
    }

    /**
     * Context keywords used for pattern matching in diagnostic text
     */
    public static final class ContextKeywords {
        private ContextKeywords() {}

        public static final String ROLLOUT = "rollout";
        public static final String DEPLOYMENT = "deployment";
        public static final String EVICT = "evict";
        public static final String DISK = "disk";
        public static final String OOM_KILLED = "OOMKilled";
    }

    /**
     * Signal extraction thresholds
     */
    public static final class SignalThresholds {
        private SignalThresholds() {}

        public static final double HEAP_USAGE_MAX_RATIO = 1.0;
        public static final double PERCENTAGE_DIVISOR = 100.0;
        public static final int RESTART_TO_SECONDS_MULTIPLIER = 300;
        public static final int MIN_GC_VALUES_FOR_TREND = 3;
        public static final double GC_RISE_RATIO_THRESHOLD = 0.5;
        public static final int FULL_GC_FREQUENT_THRESHOLD = 10;
    }

    /**
     * Validation aggregation weights and thresholds
     */
    public static final class Aggregation {
        private Aggregation() {}

        // PATH A / PATH B weights
        public static final double ASSERTION_WEIGHT = 0.4;
        public static final double RULE_WEIGHT = 0.6;

        // Score-to-status thresholds
        public static final double SUPPORTED_SCORE_THRESHOLD = 0.75;
        public static final double PARTIALLY_SUPPORTED_SCORE_THRESHOLD = 0.4;

        // Assertion status ratio thresholds
        public static final double SUPPORTED_RATIO_THRESHOLD = 0.7;
        public static final double UNSUPPORTED_RATIO_THRESHOLD = 0.7;
        public static final double PARTIAL_SUPPORT_RATIO_THRESHOLD = 0.4;
        public static final double PARTIAL_SUPPORT_WEIGHT_FACTOR = 0.5;

        // Status-to-score mapping
        public static final double SCORE_SUPPORTED = 1.0;
        public static final double SCORE_PARTIALLY_SUPPORTED = 0.5;
        public static final double SCORE_UNSUPPORTED = 0.0;
        public static final double SCORE_UNKNOWN = 0.25;

        // Aggregation explanations
        public static final String BOTH_SUPPORT = "Both paths support the RCA hypothesis";
        public static final String BOTH_REJECT = "Both paths reject the RCA hypothesis";
        public static final String NO_ASSERTIONS = "No assertions to validate";
        public static final String ASSERTION_HIGHER_CONFIDENCE = "Assertion-based validation has higher confidence";
        public static final String RULE_HIGHER_CONFIDENCE = "Rule-based validation has higher confidence";
        public static final String RULE_DETERMINISTIC = "Rule-based validation provides deterministic verdict";
        public static final String RULE_INCONCLUSIVE = "Rule-based inconclusive, using assertion-based verdict";
    }

    /**
     * Ruleset paths
     */
    public static final class RulesetPaths {
        private RulesetPaths() {}

        public static final String OOM_KILLED = "rulesets/oom-killed.yml";
        public static final String GC_PAUSE = "rulesets/gc-pause.yml";
    }
}
