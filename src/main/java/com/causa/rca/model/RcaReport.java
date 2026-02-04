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
     * Represents how confident the validation agent is in the analysis and proposed
     * solution.
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                           RCA REPORT                                               ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Title: %-76s║\n", truncate(title, 76)));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Issue Description:                                                                 ║\n");
        appendWrapped(sb, issue, 86);
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Evidence:                                                                          ║\n");
        appendWrapped(sb, evidence, 86);
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Proposed Solution:                                                                 ║\n");
        appendWrapped(sb, proposedSolution, 86);
        if (supportedLogs != null && !supportedLogs.isEmpty()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ Supported Logs:                                                                    ║\n");
            for (String log : supportedLogs) {
                appendWrapped(sb, "  • " + log, 86);
            }
        }
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Validation Confidence: %-60.2f║\n",
                validationConfidence != null ? validationConfidence : 0.0));
        sb.append("╚════════════════════════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    private String truncate(String str, int maxLength) {
        if (str == null)
            return "";
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }

    private void appendWrapped(StringBuilder sb, String text, int width) {
        if (text == null || text.isEmpty()) {
            StringBuilder naLine = new StringBuilder("║ N/A");
            while (naLine.length() < width - 1) {
                naLine.append(" ");
            }
            naLine.append("║\n");
            sb.append(naLine);
            return;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder("║ ");
        for (String word : words) {
            if (line.length() + word.length() + 1 >= width - 1) {
                // Pad the line to width - 1
                while (line.length() < width - 1) {
                    line.append(" ");
                }
                line.append("║\n");
                sb.append(line);
                line = new StringBuilder("║ ");
            }
            line.append(word).append(" ");
        }
        // Pad and append the last line
        if (line.length() > 2) {
            while (line.length() < width - 1) {
                line.append(" ");
            }
            line.append("║\n");
            sb.append(line);
        }
    }
}
