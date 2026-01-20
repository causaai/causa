package com.causa.rca.ai;

import com.causa.rca.model.RcaReport;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for validating and formatting RCA analysis results.
 * <p>
 * This service performs the final step in the RCA pipeline by critiquing the root cause
 * analysis output and formatting it into a structured {@link RcaReport} object. It ensures
 * the analysis is comprehensive, logical, and properly formatted for consumption.
 * </p>
 * <p>
 * The validation includes reviewing the proposed fix for feasibility and completeness,
 * then structuring all the information (issue, evidence, logs, solution, confidence)
 * into a standardized report format.
 * </p>
 *
 * @see AnomalyDetector
 * @see RootCauseAnalyst
 * @see RcaReport
 */
@RegisterAiService(modelName = "validator")
public interface ValidationAgent {

    /**
     * Validates the RCA output and formats it into a structured report.
     * <p>
     * Reviews the root cause analysis for quality and completeness, provides a critique
     * of the proposed solution, and then formats all information into a structured
     * {@link RcaReport} object with proper fields including title, issue description,
     * evidence, supporting logs, proposed solution, and validation confidence score.
     * </p>
     *
     * @param rcaOutput the raw root cause analysis output from the {@link RootCauseAnalyst}
     *                  containing the detailed analysis and proposed fix
     * @param fullContext the complete original context data including pod status, events,
     *                    metrics, logs, and JFR analysis used for validation
     * @return a structured {@link RcaReport} object containing the validated and formatted
     *         RCA information ready for presentation
     */
    @UserMessage("You are the Validation Agent. Your task is to critique the RCA output and then format the original data and the RCA output into the final RCAReport JSON schema.\n\nCRITIQUE: Start with a brief critique of the proposed fix.\n\nRCA Output to Validate: {rcaOutput}\n\nOriginal Context:\nFULL CONTEXT: {fullContext}")
    RcaReport validateAndFormat(@V("rcaOutput") String rcaOutput, @V("fullContext") String fullContext);
}
