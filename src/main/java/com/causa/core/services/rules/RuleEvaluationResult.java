package com.causa.core.services.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule Evaluation Result.
 *
 * <p>Contains the outcome of evaluating a single rule against observability signals.
 *
 * <p>A result records both the signals that <em>matched</em> the rule and the signals the rule
 * <em>inspected</em> — those of the right type and name, whatever their value. The two differ
 * only on failure, and that difference is the whole point: a rule can fail because the signal
 * held the wrong value ("exit code was 143, not 137") or because the signal was never present
 * at all. The first is a finding; the second is a gap in the data.
 *
 * @since 0.0.1
 */
public class RuleEvaluationResult {

    private final Rule rule;
    private final boolean passed;
    private final List<Signal> matchedSignals;
    private final List<Signal> inspectedSignals;
    private final String reasoning;

    private RuleEvaluationResult(
        Rule rule,
        boolean passed,
        List<Signal> matchedSignals,
        List<Signal> inspectedSignals,
        String reasoning
    ) {
        this.rule = rule;
        this.passed = passed;
        this.matchedSignals = matchedSignals != null ? new ArrayList<>(matchedSignals) : new ArrayList<>();
        this.inspectedSignals = inspectedSignals != null ? new ArrayList<>(inspectedSignals) : new ArrayList<>();
        this.reasoning = reasoning;
    }

    public static Builder builder(Rule rule) {
        return new Builder(rule);
    }

    public static RuleEvaluationResult passed(Rule rule, List<Signal> matchedSignals, String reasoning) {
        return new Builder(rule)
            .passed(true)
            .matchedSignals(matchedSignals)
            .inspectedSignals(matchedSignals)
            .reasoning(reasoning)
            .build();
    }

    public static RuleEvaluationResult failed(Rule rule, String reasoning) {
        return failed(rule, List.of(), reasoning);
    }

    /**
     * Creates a failed result that records the signals the rule looked at.
     *
     * @param rule             the rule that did not pass
     * @param inspectedSignals signals of the expected type and name whose value did not match;
     *                         empty when no such signal was present at all
     * @param reasoning        human-readable failure message
     */
    public static RuleEvaluationResult failed(Rule rule, List<Signal> inspectedSignals, String reasoning) {
        return new Builder(rule)
            .passed(false)
            .inspectedSignals(inspectedSignals)
            .reasoning(reasoning)
            .build();
    }

    public Rule getRule() {
        return rule;
    }

    public boolean isPassed() {
        return passed;
    }

    public List<Signal> getMatchedSignals() {
        return new ArrayList<>(matchedSignals);
    }

    /**
     * Signals of the expected type and name that the rule examined, regardless of outcome.
     *
     * <p>Empty on a failed rule means the signal was never extracted — the fact could not be
     * checked. Non-empty means it was checked and did not hold.
     */
    public List<Signal> getInspectedSignals() {
        return new ArrayList<>(inspectedSignals);
    }

    public String getReasoning() {
        return reasoning;
    }

    public int getWeightContribution() {
        return passed ? rule.getWeight() : 0;
    }

    @Override
    public String toString() {
        return String.format(
            "RuleResult[rule=%s, passed=%s, signals=%d, reasoning=%s]",
            rule.getId(),
            passed,
            matchedSignals.size(),
            reasoning
        );
    }

    public static class Builder {
        private final Rule rule;
        private boolean passed;
        private List<Signal> matchedSignals = new ArrayList<>();
        private List<Signal> inspectedSignals = new ArrayList<>();
        private String reasoning;

        private Builder(Rule rule) {
            this.rule = rule;
        }

        public Builder passed(boolean passed) {
            this.passed = passed;
            return this;
        }

        public Builder matchedSignals(List<Signal> signals) {
            if (signals != null) {
                this.matchedSignals = new ArrayList<>(signals);
            }
            return this;
        }

        public Builder addMatchedSignal(Signal signal) {
            this.matchedSignals.add(signal);
            return this;
        }

        public Builder inspectedSignals(List<Signal> signals) {
            if (signals != null) {
                this.inspectedSignals = new ArrayList<>(signals);
            }
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public RuleEvaluationResult build() {
            return new RuleEvaluationResult(rule, passed, matchedSignals, inspectedSignals, reasoning);
        }
    }
}
