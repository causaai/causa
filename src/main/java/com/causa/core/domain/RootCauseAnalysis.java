package com.causa.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Root Cause Analysis Domain Model
 *
 * <p>Represents the structured output from the LLM-based RCA process.
 * This matches the expected JSON schema from the RCA prompt template.
 *
 * @since 0.0.1
 */
public record RootCauseAnalysis(
    @JsonProperty("issue_title")
    String issueTitle,

    @JsonProperty("issue_description")
    String issueDescription,

    @JsonProperty("technical_description")
    String technicalDescription,

    @JsonProperty("anomaly_type")
    AnomalyType anomalyType,

    @JsonProperty("root_cause")
    String rootCause,

    @JsonProperty("supporting_logs")
    List<String> supportingLogs,

    @JsonProperty("evidences")
    List<String> evidences,

    @JsonProperty("possible_solutions")
    List<Solution> possibleSolutions,

    @JsonProperty("llm_confidence_score_for_rca")
    double llmConfidenceScoreForRca,

    @JsonProperty("llm_confidence_score_for_solution")
    double llmConfidenceScoreForSolution,

    @JsonProperty("confidence_summary")
    String confidenceSummary,

    @JsonProperty("llm_notes")
    String llmNotes
) {

    /**
     * Anomaly Type Classification
     */
    public enum AnomalyType {
        @JsonProperty("OOM_KILLED")
        OOM_KILLED,

        @JsonProperty("POSSIBLE_OOM_KILLED")
        POSSIBLE_OOM_KILLED,

        @JsonProperty("POSSIBLE_GC_PAUSE")
        POSSIBLE_GC_PAUSE,

        @JsonProperty("HEALTHY")
        HEALTHY
    }

    /**
     * Solution Record
     */
    public record Solution(
        @JsonProperty("solution")
        String solution,

        @JsonProperty("justification")
        String justification,

        @JsonProperty("success_probability")
        SuccessProbability successProbability,

        @JsonProperty("implementation_notes")
        String implementationNotes
    ) {

        /**
         * Success Probability Enum
         */
        public enum SuccessProbability {
            @JsonProperty("High")
            HIGH,

            @JsonProperty("Medium")
            MEDIUM,

            @JsonProperty("Low")
            LOW
        }
    }
}
