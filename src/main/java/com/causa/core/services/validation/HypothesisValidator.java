package com.causa.core.services.validation;

import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.services.rules.HypothesisValidationResult;

/**
 * Hypothesis Validator Interface.
 *
 * <p>Validates an RCA hypothesis using deterministic rule-based approach.
 *
 * <p>This runs in parallel with assertion-based validation to provide
 * dual validation paths:
 * <ul>
 *   <li><strong>Assertion-based:</strong> LLM extracts assertions, validates each</li>
 *   <li><strong>Hypothesis-based:</strong> Rules validate the hypothesis directly</li>
 * </ul>
 *
 * @since 0.0.1
 */
public interface HypothesisValidator {

    /**
     * Validate an RCA hypothesis against diagnostic context.
     *
     * @param rca               the root cause analysis with hypothesis
     * @param diagnosticContext the diagnostic context (logs, events, metrics)
     * @return validation result with score and verdict
     */
    HypothesisValidationResult validateHypothesis(
        RootCauseAnalysis rca,
        String diagnosticContext
    );
}
