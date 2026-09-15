package com.causa.core.services.rules;

import java.util.List;

/**
 * Rule - Validation Rule Interface.
 *
 * <p>A rule evaluates observability signals to determine if a specific condition is met.
 *
 * <p>Each rule has:
 * <ul>
 *   <li><strong>id:</strong> Unique identifier</li>
 *   <li><strong>description:</strong> Human-readable explanation</li>
 *   <li><strong>type:</strong> REQUIRED, SUPPORTING, or EXCLUSION</li>
 *   <li><strong>weight:</strong> Score contribution (positive for supporting, negative for exclusion)</li>
 *   <li><strong>evaluate:</strong> Logic that examines signals and returns pass/fail</li>
 * </ul>
 *
 * @since 0.0.1
 */
public interface Rule {

    /**
     * Unique rule identifier.
     *
     * @return rule ID (e.g., "oom.required.exit_code_137")
     */
    String getId();

    /**
     * Human-readable rule description.
     *
     * @return description explaining what this rule checks
     */
    String getDescription();

    /**
     * Rule type classification.
     *
     * @return REQUIRED, SUPPORTING, or EXCLUSION
     */
    RuleType getType();

    /**
     * Weight for scoring.
     *
     * <p>For SUPPORTING rules: positive weight (e.g., +2, +5)
     * <p>For EXCLUSION rules: negative weight (e.g., -3, -10)
     * <p>For REQUIRED rules: weight is typically ignored (acts as gate)
     *
     * @return weight value
     */
    int getWeight();

    /**
     * Evaluate this rule against the provided signals.
     *
     * @param signals list of normalized observability signals
     * @return result containing pass/fail, matched signals, and reasoning
     */
    RuleEvaluationResult evaluate(List<Signal> signals);

    /**
     * Base abstract implementation with common fields.
     */
    abstract class BaseRule implements Rule {
        protected final String id;
        protected final String description;
        protected final RuleType type;
        protected final int weight;

        protected BaseRule(String id, String description, RuleType type, int weight) {
            this.id = id;
            this.description = description;
            this.type = type;
            this.weight = weight;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public RuleType getType() {
            return type;
        }

        @Override
        public int getWeight() {
            return weight;
        }
    }
}
