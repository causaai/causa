package com.causa.core.services.validation;

import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.ValidatedRCA;

/**
 * Validates RCA output against collected diagnostic context.
 *
 * <p>Orchestrates the validation pipeline:
 * <ol>
 *   <li>Extract assertions from RCA</li>
 *   <li>Find evidence for each assertion</li>
 *   <li>Score and validate assertions</li>
 *   <li>Generate validated RCA with confidence scores</li>
 * </ol>
 *
 * @since 0.0.1
 */
public interface RcaValidator {

    /**
     * Validates an RCA against diagnostic context.
     *
     * @param rca the root cause analysis to validate
     * @param diagnosticContext the collected MCP context
     * @return validated RCA with assertion-level validation results
     */
    ValidatedRCA validate(RootCauseAnalysis rca, String diagnosticContext);

    /**
     * Validates an RCA with configurable confidence threshold.
     *
     * @param rca the root cause analysis
     * @param diagnosticContext the diagnostic context
     * @param minimumConfidence minimum confidence threshold (0.0 to 1.0)
     * @return validated RCA
     */
    ValidatedRCA validate(
        RootCauseAnalysis rca,
        String diagnosticContext,
        double minimumConfidence
    );
}
