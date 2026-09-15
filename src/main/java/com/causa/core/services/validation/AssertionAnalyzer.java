package com.causa.core.services.validation;

import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.ValidationResult;

/**
 * Analyzes and verifies assertions against diagnostic context.
 *
 * <p>Uses intelligent analysis (LLM-based or rule-based) to verify each assertion
 * by asking targeted questions and finding evidence in the context.
 *
 * @since 0.0.1
 */
public interface AssertionAnalyzer {

    /**
     * Analyzes a single assertion against diagnostic context.
     *
     * <p>Asks targeted questions about the assertion and searches for evidence
     * to verify the claim.
     *
     * @param assertion the assertion to verify
     * @param diagnosticContext the collected MCP context
     * @return validation result with evidence and confidence
     */
    ValidationResult analyze(Assertion assertion, String diagnosticContext);

    /**
     * Analyzes multiple assertions in batch.
     *
     * <p>May optimize by processing assertions together or in parallel.
     *
     * @param assertions list of assertions to analyze
     * @param diagnosticContext the diagnostic context
     * @return list of validation results (same order as input)
     */
    java.util.List<ValidationResult> analyzeAll(
        java.util.List<Assertion> assertions,
        String diagnosticContext
    );
}
