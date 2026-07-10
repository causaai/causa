package com.causa.core.services.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hypothesis Validation Result.
 *
 * <p>Final verdict from rule-based validation including:
 * <ul>
 *   <li>Overall status (SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED)</li>
 *   <li>Confidence score (0.0 - 1.0)</li>
 *   <li>Individual rule evaluation results</li>
 *   <li>Total weighted score</li>
 *   <li>Explanation of verdict</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class HypothesisValidationResult {

    public enum ValidationStatus {
        /**
         * All required rules passed, high overall score, strong supporting evidence.
         */
        SUPPORTED,

        /**
         * Required rules passed, some supporting evidence, but insufficient for high confidence.
         */
        PARTIALLY_SUPPORTED,

        /**
         * Required rules failed, or strong exclusion evidence, or score below threshold.
         */
        UNSUPPORTED
    }

    private final String hypothesis;
    private final ValidationStatus status;
    private final double confidence;
    private final int totalScore;
    private final int maxPossibleScore;
    private final double normalizedScore;
    private final ScoreBreakdown scoreBreakdown;
    private final List<RuleEvaluationResult> requiredResults;
    private final List<RuleEvaluationResult> supportingResults;
    private final List<RuleEvaluationResult> exclusionResults;
    private final String explanation;

    private HypothesisValidationResult(
        String hypothesis,
        ValidationStatus status,
        double confidence,
        int totalScore,
        int maxPossibleScore,
        double normalizedScore,
        ScoreBreakdown scoreBreakdown,
        List<RuleEvaluationResult> requiredResults,
        List<RuleEvaluationResult> supportingResults,
        List<RuleEvaluationResult> exclusionResults,
        String explanation
    ) {
        this.hypothesis = hypothesis;
        this.status = status;
        this.confidence = confidence;
        this.totalScore = totalScore;
        this.maxPossibleScore = maxPossibleScore;
        this.normalizedScore = normalizedScore;
        this.scoreBreakdown = scoreBreakdown;
        this.requiredResults = new ArrayList<>(requiredResults);
        this.supportingResults = new ArrayList<>(supportingResults);
        this.exclusionResults = new ArrayList<>(exclusionResults);
        this.explanation = explanation;
    }

    /**
     * Score breakdown showing contribution from each rule type.
     */
    public static class ScoreBreakdown {
        private final int requiredScore;
        private final int supportingScore;
        private final int exclusionScore;

        public ScoreBreakdown(int requiredScore, int supportingScore, int exclusionScore) {
            this.requiredScore = requiredScore;
            this.supportingScore = supportingScore;
            this.exclusionScore = exclusionScore;
        }

        public int getRequiredScore() {
            return requiredScore;
        }

        public int getSupportingScore() {
            return supportingScore;
        }

        public int getExclusionScore() {
            return exclusionScore;
        }
    }

    public static Builder builder(String hypothesis) {
        return new Builder(hypothesis);
    }

    public String getHypothesis() {
        return hypothesis;
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public int getMaxPossibleScore() {
        return maxPossibleScore;
    }

    public double getNormalizedScore() {
        return normalizedScore;
    }

    public ScoreBreakdown getScoreBreakdown() {
        return scoreBreakdown;
    }

    public List<RuleEvaluationResult> getRequiredResults() {
        return new ArrayList<>(requiredResults);
    }

    public List<RuleEvaluationResult> getSupportingResults() {
        return new ArrayList<>(supportingResults);
    }

    public List<RuleEvaluationResult> getExclusionResults() {
        return new ArrayList<>(exclusionResults);
    }

    public String getExplanation() {
        return explanation;
    }

    public long getRequiredPassed() {
        return requiredResults.stream().filter(RuleEvaluationResult::isPassed).count();
    }

    public long getRequiredTotal() {
        return requiredResults.size();
    }

    public long getSupportingMatched() {
        return supportingResults.stream().filter(RuleEvaluationResult::isPassed).count();
    }

    public long getExclusionMatched() {
        return exclusionResults.stream().filter(RuleEvaluationResult::isPassed).count();
    }

    public boolean allRequiredPassed() {
        return requiredResults.stream().allMatch(RuleEvaluationResult::isPassed);
    }

    public List<RuleEvaluationResult> getAllResults() {
        List<RuleEvaluationResult> all = new ArrayList<>();
        all.addAll(requiredResults);
        all.addAll(supportingResults);
        all.addAll(exclusionResults);
        return all;
    }

    public String toSummaryString() {
        return String.format(
            "%s (confidence=%.2f, score=%d, required=%d/%d, supporting=%d, exclusion=%d)",
            status,
            confidence,
            totalScore,
            getRequiredPassed(),
            getRequiredTotal(),
            getSupportingMatched(),
            getExclusionMatched()
        );
    }

    @Override
    public String toString() {
        return String.format(
            "HypothesisValidation[hypothesis=%s, status=%s, confidence=%.2f, score=%d]",
            hypothesis,
            status,
            confidence,
            totalScore
        );
    }

    public static class Builder {
        private final String hypothesis;
        private ValidationStatus status;
        private double confidence;
        private int totalScore;
        private int maxPossibleScore;
        private double normalizedScore;
        private ScoreBreakdown scoreBreakdown;
        private List<RuleEvaluationResult> requiredResults = new ArrayList<>();
        private List<RuleEvaluationResult> supportingResults = new ArrayList<>();
        private List<RuleEvaluationResult> exclusionResults = new ArrayList<>();
        private String explanation;

        private Builder(String hypothesis) {
            this.hypothesis = hypothesis;
        }

        public Builder status(ValidationStatus status) {
            this.status = status;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder totalScore(int totalScore) {
            this.totalScore = totalScore;
            return this;
        }

        public Builder maxPossibleScore(int maxPossibleScore) {
            this.maxPossibleScore = maxPossibleScore;
            return this;
        }

        public Builder normalizedScore(double normalizedScore) {
            this.normalizedScore = normalizedScore;
            return this;
        }

        public Builder scoreBreakdown(ScoreBreakdown scoreBreakdown) {
            this.scoreBreakdown = scoreBreakdown;
            return this;
        }

        public Builder requiredResults(List<RuleEvaluationResult> results) {
            this.requiredResults = new ArrayList<>(results);
            return this;
        }

        public Builder supportingResults(List<RuleEvaluationResult> results) {
            this.supportingResults = new ArrayList<>(results);
            return this;
        }

        public Builder exclusionResults(List<RuleEvaluationResult> results) {
            this.exclusionResults = new ArrayList<>(results);
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = explanation;
            return this;
        }

        public HypothesisValidationResult build() {
            return new HypothesisValidationResult(
                hypothesis,
                status,
                confidence,
                totalScore,
                maxPossibleScore,
                normalizedScore,
                scoreBreakdown,
                requiredResults,
                supportingResults,
                exclusionResults,
                explanation
            );
        }
    }
}
