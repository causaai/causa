package com.causa.common.constants;

/**
 * Diagnostic Constants
 *
 * <p>Contains constants and enums for diagnostic analysis, fault domains, and diagnostic status.
 *
 * @since 0.0.1
 */
public final class DiagnosticConstants {

    private DiagnosticConstants() {
        // Prevent instantiation
    }

    /**
     * Logging field names for diagnostics.
     */
    public static final class Fields {
        private Fields() {}

        public static final String DIAGNOSTIC_ID = "diagnosticId";
    }

    // Logging format constants
    public static final int SEPARATOR_LENGTH = 80;
    public static final String SEPARATOR_CHAR = "=";
    public static final String CONTEXT_HEADER = "COLLECTED DIAGNOSTIC CONTEXT (LLM-Ready)";
    public static final String NEWLINE = "\n";



    /**
     * Diagnostic Status
     *
     * <p>Defines the lifecycle status of a diagnostic analysis.
     */
    public enum DiagnosticStatus {

        PENDING("PENDING"),
        IN_PROGRESS("IN_PROGRESS"),
        COMPLETED("COMPLETED"),
        FAILED("FAILED");

        private final String value;

        DiagnosticStatus(String value) {
            this.value = value;
        }

        /**
         * Returns the string value of this status.
         *
         * @return the status value
         */
        public String getValue() {
            return value;
        }

        /**
         * Converts a string to a DiagnosticStatus enum value (case-insensitive).
         *
         * @param value the string value
         * @return the corresponding DiagnosticStatus
         * @throws IllegalArgumentException if the value doesn't match any status
         */
        public static DiagnosticStatus fromString(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Diagnostic status value cannot be null or blank");
            }

            String normalized = value.trim().toUpperCase();

            for (DiagnosticStatus status : DiagnosticStatus.values()) {
                if (status.value.equals(normalized)) {
                    return status;
                }
            }

            throw new IllegalArgumentException("Unknown diagnostic status: " + value);
        }
    }

    /**
     * Fault Domain
     *
     * <p>Specific memory-related diagnostic categories for alert classification.
     */
    public enum FaultDomain {

        OOM_KILLED("OOM_KILLED"),
        HIGH_MEMORY_PRESSURE("HIGH_MEMORY_PRESSURE"),
        POSSIBLE_OOM_KILLED("POSSIBLE_OOM_KILLED"),
        POSSIBLE_HIGH_MEMORY_PRESSURE("POSSIBLE_HIGH_MEMORY_PRESSURE"),
        POSSIBLE_GC_PAUSE("POSSIBLE_GC_PAUSE");

        private final String value;

        FaultDomain(String value) {
            this.value = value;
        }

        /**
         * Returns the string value of this fault domain.
         *
         * @return the fault domain value
         */
        public String getValue() {
            return value;
        }

        /**
         * Converts a string to a FaultDomain enum value (case-insensitive).
         *
         * @param value the string value
         * @return the corresponding FaultDomain
         * @throws IllegalArgumentException if the value doesn't match any fault domain
         */
        public static FaultDomain fromString(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Fault domain value cannot be null or blank");
            }

            String normalized = value.trim().toUpperCase();

            for (FaultDomain domain : FaultDomain.values()) {
                if (domain.value.equals(normalized)) {
                    return domain;
                }
            }

            throw new IllegalArgumentException("Unknown fault domain: " + value);
        }
    }
}
