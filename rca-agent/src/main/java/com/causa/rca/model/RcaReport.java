package com.causa.rca.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Data model representing a Root Cause Analysis (RCA) report.
 * <p>
 * This class encapsulates the complete results of an RCA analysis, including the identified
 * issue, supporting evidence, relevant logs, proposed solution, and a confidence score.
 * It serves as the final output of the RCA pipeline and is returned to clients via the REST API.
 * </p>
 * <p>
 * The class is registered for reflection to support JSON serialization/deserialization
 * in native image builds and for use with AI services that generate structured outputs.
 * </p>
 *
 * @see com.causa.rca.ai.ValidationAgent
 * @see com.causa.rca.rest.RcaResource
 */
@RegisterForReflection
public class RcaReport {
    
    /**
     * The title or summary of the RCA report.
     * Provides a concise description of the issue being analyzed.
     */
    public String title;
    
    /**
     * Detailed description of the identified issue.
     * Explains what problem was detected and its impact on the system.
     */
    public String issue;
    
    /**
     * Evidence supporting the root cause analysis.
     * Contains metrics, observations, and data points that led to the conclusion.
     */
    public String evidence;
    
    /**
     * List of relevant log entries that support the analysis.
     * Contains specific log lines or patterns that are pertinent to the issue.
     */
    public List<String> supportedLogs;
    
    /**
     * The proposed solution to remediate the identified issue.
     * Includes actionable steps and recommendations to fix the problem.
     */
    public String proposedSolution;
    
    /**
     * Confidence score of the validation (0.0 to 1.0).
     * Represents how confident the validation agent is in the analysis and proposed solution.
     * Higher values indicate greater confidence in the RCA results.
     */
    public Double validationConfidence;

    /**
     * Default constructor for JSON deserialization and reflection.
     */
    public RcaReport() {
    }

    /**
     * Constructs a complete RCA report with all fields.
     *
     * @param title the title or summary of the report
     * @param issue detailed description of the identified issue
     * @param evidence supporting evidence for the root cause
     * @param supportedLogs list of relevant log entries
     * @param proposedSolution the recommended solution
     * @param validationConfidence confidence score (0.0 to 1.0)
     */
    public RcaReport(String title, String issue, String evidence, List<String> supportedLogs, String proposedSolution,
            Double validationConfidence) {
        this.title = title;
        this.issue = issue;
        this.evidence = evidence;
        this.supportedLogs = supportedLogs;
        this.proposedSolution = proposedSolution;
        this.validationConfidence = validationConfidence;
    }
}
