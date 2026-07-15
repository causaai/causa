package com.causa.core.services.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule Evaluation Result.
 *
 * <p>Contains the outcome of evaluating a single rule against observability signals.
 *
 * @since 0.0.1
 */
public class RuleEvaluationResult {

    private final Rule rule;
    private final boolean passed;
    private final List<Signal> matchedSignals;
    private final String reasoning;

    private RuleEvaluationResult(
        Rule rule,
        boolean passed,
        List<Signal> matchedSignals,
        String reasoning
    ) {
        this.rule = rule;
        this.passed = passed;
        this.matchedSignals = matchedSignals != null ? new ArrayList<>(matchedSignals) : new ArrayList<>();
        this.reasoning = reasoning;
    }

    public static Builder builder(Rule rule) {
        return new Builder(rule);
    }

    public static RuleEvaluationResult passed(Rule rule, List<Signal> matchedSignals, String reasoning) {
        return new Builder(rule)
            .passed(true)
            .matchedSignals(matchedSignals)
            .reasoning(reasoning)
            .build();
    }

    public static RuleEvaluationResult failed(Rule rule, String reasoning) {
        return new Builder(rule)
            .passed(false)
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

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public RuleEvaluationResult build() {
            return new RuleEvaluationResult(rule, passed, matchedSignals, reasoning);
        }
    }
}
