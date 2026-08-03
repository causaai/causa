package com.causa.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Root Cause Analysis Domain Model
 *
 * <p>Represents the structured JSON output from the LLM-based RCA process.
 * Field names match the prompt template output format exactly.
 *
 * @since 0.0.1
 */
public record RootCauseAnalysis(

    @JsonProperty("issue_title")
    String issueTitle,

    @JsonProperty("issue_summary")
    String issueSummary,

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
    @NotNull(message = "confidence_summary is required")
    ConfidenceSummary confidenceSummary,

    @JsonProperty("llm_notes")
    String llmNotes

) {

    // -------------------------------------------------------------------------
    // Nested records — matching prompt JSON exactly
    // -------------------------------------------------------------------------

    /**
     * Confidence summary — nested object in LLM output.
     * Shape: { "rca_confidence_score": 0.9, "summary_text": "..." }
     */
    public record ConfidenceSummary(

        @JsonProperty("rca_confidence_score")
        @NotNull(message = "rca_confidence_score is required")
        @DecimalMin(value = "0.0", message = "rca_confidence_score must be >= 0.0")
        @DecimalMax(value = "1.0", message = "rca_confidence_score must be <= 1.0")
        Double rcaConfidenceScore,

        @JsonProperty("summary_text")
        String summaryText

    ) {}

    /**
     * A single recommendation from the LLM.
     * solution_type is one of: "Immediate Mitigation", "Validate & Monitor", "Root Cause Fix"
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
        @DecimalMin(value = "0.0", message = "solution_confidence_score must be >= 0.0")
        @DecimalMax(value = "1.0", message = "solution_confidence_score must be <= 1.0")
        Double solutionConfidenceScore,

        @JsonProperty("solution_alerts")
        List<String> solutionAlerts

    ) {}

    /**
     * Anomaly type — exactly one of the four allowed values.
     */
    public enum AnomalyType {
        @JsonProperty("OOM_KILLED")            OOM_KILLED,
        @JsonProperty("POSSIBLE_OOM_KILLED")   POSSIBLE_OOM_KILLED,
        @JsonProperty("POSSIBLE_GC_PAUSE")     POSSIBLE_GC_PAUSE,
        @JsonProperty("HEALTHY")               HEALTHY
    }
}
