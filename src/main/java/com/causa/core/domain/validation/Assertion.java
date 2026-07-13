package com.causa.core.domain.validation;

import java.util.Optional;

/**
 * Represents an atomic claim extracted from RCA output.
 *
 * <p>Each assertion represents a single verifiable fact that the LLM claimed
 * in the root cause analysis. Assertions are validated independently against
 * the collected diagnostic context.
 *
 * <p>Example assertions:
 * <ul>
 *   <li>"Container was OOMKilled"</li>
 *   <li>"Heap usage continuously increased"</li>
 *   <li>"Memory limit was reached"</li>
 * </ul>
 *
 * @since 0.0.1
 */
public record Assertion(
    String id,
    String text,
    AssertionType type,
    AssertionSource source,
    Optional<String> relatedField
) {

    /**
     * Creates a new assertion.
     *
     * @param id unique identifier for this assertion
     * @param text the assertion text
     * @param type the type of assertion
     * @param source where this assertion came from in the RCA
     * @param relatedField optional field name in the RCA (e.g., "rootCause", "possibleSolutions[0]")
     */
    public Assertion {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Assertion ID cannot be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Assertion text cannot be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Assertion type cannot be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("Assertion source cannot be null");
        }
        if (relatedField == null) {
            relatedField = Optional.empty();
        }
    }

    /**
     * Creates a simple assertion without related field.
     */
    public static Assertion of(String id, String text, AssertionType type, AssertionSource source) {
        return new Assertion(id, text, type, source, Optional.empty());
    }

    /**
     * Creates an assertion with related field.
     */
    public static Assertion of(String id, String text, AssertionType type, AssertionSource source, String relatedField) {
        return new Assertion(id, text, type, source, Optional.of(relatedField));
    }

    /**
     * Type of assertion for categorization.
     */
    public enum AssertionType {
        /** Direct observation (e.g., "Container was OOMKilled") */
        OBSERVATION,

        /** Trend or pattern (e.g., "Memory usage increased over time") */
        TREND,

        /** Causal relationship (e.g., "OOMKill caused by heap exhaustion") */
        CAUSALITY,

        /** Configuration state (e.g., "Memory limit set to 512Mi") */
        CONFIGURATION,

        /** Recommendation or solution (e.g., "Increase memory limit to 1Gi") */
        RECOMMENDATION
    }

    /**
     * Source section of the RCA where this assertion originated.
     */
    public enum AssertionSource {
        /** From rootCause field */
        ROOT_CAUSE,

        /** From issueDescription field */
        ISSUE_DESCRIPTION,

        /** From technicalDescription field */
        TECHNICAL_DESCRIPTION,

        /** From possibleSolutions field */
        POSSIBLE_SOLUTIONS,

        /** From other fields */
        OTHER
    }
}
