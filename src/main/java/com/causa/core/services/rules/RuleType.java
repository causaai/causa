package com.causa.core.services.rules;

/**
 * Rule Type Classification.
 *
 * <p>Defines the three categories of validation rules:
 * <ul>
 *   <li><strong>REQUIRED:</strong> Mandatory gating conditions. All must pass for hypothesis to be valid.</li>
 *   <li><strong>SUPPORTING:</strong> Increases confidence when matched. Each adds positive weight to score.</li>
 *   <li><strong>EXCLUSION:</strong> Contradictory evidence. Each subtracts weight from score.</li>
 * </ul>
 *
 * @since 0.0.1
 */
public enum RuleType {
    /**
     * Required rules are mandatory gating conditions.
     * All required rules must pass for a hypothesis to be considered valid.
     * If any required rule fails, the hypothesis is UNSUPPORTED.
     */
    REQUIRED,

    /**
     * Supporting rules increase confidence in the hypothesis.
     * Each matched supporting rule adds positive weight to the total score.
     * More supporting evidence = higher confidence.
     */
    SUPPORTING,

    /**
     * Exclusion rules represent contradictory evidence.
     * Each matched exclusion rule subtracts weight from the total score.
     * Strong exclusion evidence can invalidate a hypothesis.
     */
    EXCLUSION
}
