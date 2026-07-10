package com.causa.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Root Cause Analysis Domain Model
 *
 * <p>Represents the structured output from the LLM-based RCA process.
 * This matches the JSON schema defined in rca-prompt-template.yml.
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

    @JsonProperty("recommendations")
    List<Recommendation> recommendations,

    @JsonProperty("confidence_summary")
    ConfidenceSummary confidenceSummary,

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
     * Confidence Summary nested object.
     * Maps to: { "rca_confidence_score": 0.85, "summary_text": "..." }
     */
    public record ConfidenceSummary(
        @JsonProperty("rca_confidence_score")
        Double rcaConfidenceScore,

        @JsonProperty("summary_text")
        String summaryText
    ) {}

    /**
     * Recommendation Record — maps to each element in the "recommendations" array.
     * Fields align with the prompt template OUTPUT FORMAT.
     */
    public record Recommendation(
        @JsonProperty("solution_type")
        String solutionType,

        @JsonProperty("solution_title")
        String solutionTitle,

        @JsonProperty("solution_description")
        String solutionDescription,

        @JsonProperty("implementation_notes")
        String implementationNotes,

        @JsonProperty("solution_confidence_score")
        Double solutionConfidenceScore,

        @JsonProperty("solution_alerts")
        List<String> solutionAlerts
    ) {}
}
