package com.causa.core.services.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule Set - Collection of rules for validating a specific hypothesis.
 *
 * <p>A RuleSet groups all validation rules (REQUIRED, SUPPORTING, EXCLUSION)
 * for a specific type of root cause hypothesis (e.g., OOMKilled, Memory Leak, GC Pause).
 *
 * @since 0.0.1
 */
public interface RuleSet {

    /**
     * Hypothesis name that this rule set validates.
     *
     * @return hypothesis name (e.g., "OOMKilled", "MemoryLeak", "GCPause")
     */
    String getHypothesisName();

    /**
     * Get all required rules.
     *
     * @return list of required (gating) rules
     */
    List<Rule> getRequiredRules();

    /**
     * Get all supporting rules.
     *
     * @return list of supporting (confidence-increasing) rules
     */
    List<Rule> getSupportingRules();

    /**
     * Get all exclusion rules.
     *
     * @return list of exclusion (contradictory evidence) rules
     */
    List<Rule> getExclusionRules();

    /**
     * Get all rules (required + supporting + exclusion).
     *
     * @return complete list of all rules
     */
    default List<Rule> getAllRules() {
        List<Rule> all = new ArrayList<>();
        all.addAll(getRequiredRules());
        all.addAll(getSupportingRules());
        all.addAll(getExclusionRules());
        return all;
    }

    /**
     * Get minimum score threshold for SUPPORTED verdict.
     *
     * @return minimum score (e.g., 10)
     */
    int getMinSupportedScore();

    /**
     * Get minimum score threshold for PARTIALLY_SUPPORTED verdict.
     *
     * @return minimum score (e.g., 5)
     */
    int getMinPartiallySupportedScore();
}
