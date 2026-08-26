package com.causa.core.services.rules;

import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.services.validation.HypothesisValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Rule Engine - Core Deterministic Validation Engine.
 *
 * <p>Evaluates hypotheses against rule sets using a weighted scoring approach:
 * <ol>
 *   <li>Evaluate all REQUIRED rules (gating conditions)</li>
 *   <li>Evaluate SUPPORTING rules (add positive weight)</li>
 *   <li>Evaluate EXCLUSION rules (subtract weight)</li>
 *   <li>Calculate total score</li>
 *   <li>Map score to verdict (SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED)</li>
 * </ol>
 *
 * <p>This engine is modular and reusable across components.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class RuleEngine implements HypothesisValidator {

    private static final CausaLogger log = CausaLogger.getLogger(RuleEngine.class);

    private static final Map<String, String> ANOMALY_TO_RULESET = Map.of(
        "OOM_KILLED", "rulesets/oom-killed.yml",
        "POSSIBLE_OOM_KILLED", "rulesets/oom-killed.yml",
        "POSSIBLE_GC_PAUSE", "rulesets/high-memory-pressure.yml"
    );

    private final Map<String, RuleSet> ruleSetCache = new ConcurrentHashMap<>();

    @Inject
    SignalExtractor signalExtractor;

    @Override
    public HypothesisValidationResult validateHypothesis(RootCauseAnalysis rca, String diagnosticContext) {
        String anomaly = rca.anomalyType() != null ? rca.anomalyType().name() : "UNKNOWN";
        String rulesetPath = ANOMALY_TO_RULESET.get(anomaly);

        if (rulesetPath == null) {
            log.info("No ruleset configured for anomaly type")
                .field("anomalyType", anomaly)
                .log();
            return HypothesisValidationResult.builder(anomaly)
                .status(HypothesisValidationResult.ValidationStatus.UNSUPPORTED)
                .confidence(0.0)
                .totalScore(0)
                .maxPossibleScore(0)
                .normalizedScore(0.0)
                .scoreBreakdown(new HypothesisValidationResult.ScoreBreakdown(0, 0, 0))
                .requiredResults(List.of())
                .supportingResults(List.of())
                .exclusionResults(List.of())
                .explanation("No ruleset available for anomaly type: " + anomaly)
                .build();
        }

        RuleSet ruleSet = ruleSetCache.computeIfAbsent(rulesetPath, this::loadRuleSetFromYaml);
        List<Signal> signals = signalExtractor.extractSignals(diagnosticContext);

        log.info("PATH B: Running rule-based hypothesis validation")
            .field("anomalyType", anomaly)
            .field("ruleset", rulesetPath)
            .field("signalCount", signals.size())
            .log();

        return validate(anomaly, ruleSet, signals);
    }

    /**
     * Validate a hypothesis using the provided rule set and signals.
     *
     * @param hypothesis  the hypothesis being tested (e.g., "OOMKilled")
     * @param ruleSet     the set of validation rules
     * @param signals     normalized observability signals
     * @return validation result with verdict, score, and explanation
     */
    public HypothesisValidationResult validate(
        String hypothesis,
        RuleSet ruleSet,
        List<Signal> signals
    ) {
        log.debug("Validating hypothesis with rule engine")
            .field("hypothesis", hypothesis)
            .field("ruleSetName", ruleSet.getHypothesisName())
            .field("signalCount", signals.size())
            .log();

        // Step 1: Evaluate REQUIRED rules
        List<RuleEvaluationResult> requiredResults = evaluateRules(
            ruleSet.getRequiredRules(),
            signals
        );

        boolean allRequiredPassed = requiredResults.stream()
            .allMatch(RuleEvaluationResult::isPassed);

        // Step 2: Evaluate SUPPORTING rules
        List<RuleEvaluationResult> supportingResults = evaluateRules(
            ruleSet.getSupportingRules(),
            signals
        );

        // Step 3: Evaluate EXCLUSION rules
        List<RuleEvaluationResult> exclusionResults = evaluateRules(
            ruleSet.getExclusionRules(),
            signals
        );

        // Step 4: Calculate weighted score
        int score = calculateScore(requiredResults, supportingResults, exclusionResults);

        // Step 4b: Calculate score breakdown
        HypothesisValidationResult.ScoreBreakdown scoreBreakdown = calculateScoreBreakdown(
            requiredResults, supportingResults, exclusionResults
        );

        // Step 4c: Calculate max possible score and normalized score
        int maxPossibleScore = calculateMaxPossibleScore(ruleSet);
        double normalizedScore = maxPossibleScore > 0 ? (double) score / maxPossibleScore : 0.0;

        // Step 5: Determine verdict
        HypothesisValidationResult.ValidationStatus status = determineStatus(
            allRequiredPassed,
            score,
            ruleSet
        );

        // Step 6: Calculate confidence
        double confidence = calculateConfidence(status, score, ruleSet);

        // Step 7: Build explanation
        String explanation = buildExplanation(
            status,
            allRequiredPassed,
            requiredResults,
            supportingResults,
            exclusionResults,
            score
        );

        HypothesisValidationResult result = HypothesisValidationResult.builder(hypothesis)
            .status(status)
            .confidence(confidence)
            .totalScore(score)
            .maxPossibleScore(maxPossibleScore)
            .normalizedScore(normalizedScore)
            .scoreBreakdown(scoreBreakdown)
            .requiredResults(requiredResults)
            .supportingResults(supportingResults)
            .exclusionResults(exclusionResults)
            .explanation(explanation)
            .build();

        log.info("Rule-based validation completed")
            .field("hypothesis", hypothesis)
            .field("status", status)
            .field("confidence", confidence)
            .field("score", score)
            .field("requiredPassed", result.getRequiredPassed())
            .field("requiredTotal", result.getRequiredTotal())
            .field("supportingMatched", result.getSupportingMatched())
            .field("exclusionMatched", result.getExclusionMatched())
            .log();

        return result;
    }

    /**
     * Evaluate a list of rules against signals.
     */
    private List<RuleEvaluationResult> evaluateRules(List<Rule> rules, List<Signal> signals) {
        return rules.stream()
            .map(rule -> {
                try {
                    return rule.evaluate(signals);
                } catch (Exception e) {
                    log.error("Rule evaluation failed")
                        .field("ruleId", rule.getId())
                        .exception(e)
                        .log();
                    return RuleEvaluationResult.failed(rule, "Evaluation error: " + e.getMessage());
                }
            })
            .collect(Collectors.toList());
    }

    /**
     * Calculate total weighted score.
     *
     * <p>Score = (Required passed) + Sum(Supporting weights) - Sum(Exclusion weights)
     */
    private int calculateScore(
        List<RuleEvaluationResult> requiredResults,
        List<RuleEvaluationResult> supportingResults,
        List<RuleEvaluationResult> exclusionResults
    ) {
        // Required rules contribute their weight if passed (or can be treated as binary gate)
        int requiredScore = (int) requiredResults.stream()
            .filter(RuleEvaluationResult::isPassed)
            .count();

        // Supporting rules add positive weight
        int supportingScore = supportingResults.stream()
            .filter(RuleEvaluationResult::isPassed)
            .mapToInt(RuleEvaluationResult::getWeightContribution)
            .sum();

        // Exclusion rules subtract weight (their weights are negative)
        int exclusionScore = exclusionResults.stream()
            .filter(RuleEvaluationResult::isPassed)
            .mapToInt(r -> r.getRule().getWeight())  // Already negative
            .sum();

        return requiredScore + supportingScore + exclusionScore;
    }

    /**
     * Calculate score breakdown showing contribution from each rule type.
     */
    private HypothesisValidationResult.ScoreBreakdown calculateScoreBreakdown(
        List<RuleEvaluationResult> requiredResults,
        List<RuleEvaluationResult> supportingResults,
        List<RuleEvaluationResult> exclusionResults
    ) {
        int requiredScore = (int) requiredResults.stream()
            .filter(RuleEvaluationResult::isPassed)
            .count();

        int supportingScore = supportingResults.stream()
            .filter(RuleEvaluationResult::isPassed)
            .mapToInt(RuleEvaluationResult::getWeightContribution)
            .sum();

        int exclusionScore = exclusionResults.stream()
            .filter(RuleEvaluationResult::isPassed)
            .mapToInt(r -> r.getRule().getWeight())
            .sum();

        return new HypothesisValidationResult.ScoreBreakdown(
            requiredScore,
            supportingScore,
            exclusionScore
        );
    }

    /**
     * Calculate maximum possible score for normalization.
     *
     * <p>Max score = (all required passed) + sum(all supporting weights) + 0 (exclusions don't add to max)
     */
    private int calculateMaxPossibleScore(RuleSet ruleSet) {
        // All required rules passed
        int maxRequired = ruleSet.getRequiredRules().size();

        // All supporting rules matched
        int maxSupporting = ruleSet.getSupportingRules().stream()
            .mapToInt(Rule::getWeight)
            .sum();

        // Exclusions don't contribute to max (they only subtract)
        return maxRequired + maxSupporting;
    }

    /**
     * Determine validation status based on required rules and score.
     */
    private HypothesisValidationResult.ValidationStatus determineStatus(
        boolean allRequiredPassed,
        int score,
        RuleSet ruleSet
    ) {
        // If any required rule failed, hypothesis is UNSUPPORTED
        if (!allRequiredPassed) {
            return HypothesisValidationResult.ValidationStatus.UNSUPPORTED;
        }

        // If score meets SUPPORTED threshold
        if (score >= ruleSet.getMinSupportedScore()) {
            return HypothesisValidationResult.ValidationStatus.SUPPORTED;
        }

        // If score meets PARTIALLY_SUPPORTED threshold
        if (score >= ruleSet.getMinPartiallySupportedScore()) {
            return HypothesisValidationResult.ValidationStatus.PARTIALLY_SUPPORTED;
        }

        // Score too low
        return HypothesisValidationResult.ValidationStatus.UNSUPPORTED;
    }

    /**
     * Calculate confidence score (0.0 - 1.0) based on status and score.
     */
    private double calculateConfidence(
        HypothesisValidationResult.ValidationStatus status,
        int score,
        RuleSet ruleSet
    ) {
        return switch (status) {
            case SUPPORTED -> {
                // Map score to 0.8 - 1.0 range
                int supportedThreshold = ruleSet.getMinSupportedScore();
                int maxScore = supportedThreshold + 20;  // Assume max is threshold + 20
                double normalized = Math.min(1.0, (double) score / maxScore);
                yield 0.8 + (normalized * 0.2);
            }
            case PARTIALLY_SUPPORTED -> {
                // Map score to 0.5 - 0.79 range
                int partialThreshold = ruleSet.getMinPartiallySupportedScore();
                int supportedThreshold = ruleSet.getMinSupportedScore();
                double normalized = (double) (score - partialThreshold) / (supportedThreshold - partialThreshold);
                yield 0.5 + (normalized * 0.29);
            }
            case UNSUPPORTED -> {
                // Low confidence (0.0 - 0.49)
                int partialThreshold = ruleSet.getMinPartiallySupportedScore();
                if (score <= 0) {
                    yield 0.0;
                }
                yield Math.min(0.49, (double) score / partialThreshold * 0.49);
            }
        };
    }

    /**
     * Build human-readable explanation of verdict.
     */
    private String buildExplanation(
        HypothesisValidationResult.ValidationStatus status,
        boolean allRequiredPassed,
        List<RuleEvaluationResult> requiredResults,
        List<RuleEvaluationResult> supportingResults,
        List<RuleEvaluationResult> exclusionResults,
        int score
    ) {
        StringBuilder explanation = new StringBuilder();

        // Required rules summary
        long requiredPassed = requiredResults.stream().filter(RuleEvaluationResult::isPassed).count();
        explanation.append(String.format("Required: %d/%d passed. ", requiredPassed, requiredResults.size()));

        if (!allRequiredPassed) {
            List<String> failed = requiredResults.stream()
                .filter(r -> !r.isPassed())
                .map(r -> r.getRule().getId())
                .collect(Collectors.toList());
            explanation.append("Failed required rules: ").append(String.join(", ", failed)).append(". ");
        }

        // Supporting rules summary
        long supportingMatched = supportingResults.stream().filter(RuleEvaluationResult::isPassed).count();
        explanation.append(String.format("Supporting: %d matched. ", supportingMatched));

        // Exclusion rules summary
        long exclusionMatched = exclusionResults.stream().filter(RuleEvaluationResult::isPassed).count();
        if (exclusionMatched > 0) {
            explanation.append(String.format("Exclusion: %d contradictory evidence found. ", exclusionMatched));
        }

        // Final verdict
        explanation.append(String.format("Total score: %d. Verdict: %s.", score, status));

        return explanation.toString();
    }

    @SuppressWarnings("unchecked")
    private RuleSet loadRuleSetFromYaml(String path) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.error("Ruleset file not found").field("path", path).log();
                return emptyRuleSet("UNKNOWN");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(is);

            String hypothesis = (String) data.getOrDefault("hypothesis", "UNKNOWN");
            Map<String, Object> thresholds = (Map<String, Object>) data.getOrDefault("thresholds", Map.of());
            int minSupported = ((Number) thresholds.getOrDefault("minSupportedScore", 10)).intValue();
            int minPartial = ((Number) thresholds.getOrDefault("minPartiallySupportedScore", 5)).intValue();

            List<Rule> required = parseRules((List<Map<String, Object>>) data.getOrDefault("required", List.of()), RuleType.REQUIRED);
            List<Rule> supporting = parseRules((List<Map<String, Object>>) data.getOrDefault("supporting", List.of()), RuleType.SUPPORTING);
            List<Rule> exclusion = parseRules((List<Map<String, Object>>) data.getOrDefault("exclusion", List.of()), RuleType.EXCLUSION);

            log.info("Loaded ruleset from YAML")
                .field("path", path)
                .field("hypothesis", hypothesis)
                .field("required", required.size())
                .field("supporting", supporting.size())
                .field("exclusion", exclusion.size())
                .log();

            return new LoadedRuleSet(hypothesis, required, supporting, exclusion, minSupported, minPartial);
        } catch (Exception e) {
            log.error("Failed to load ruleset").field("path", path).exception(e).log();
            return emptyRuleSet("UNKNOWN");
        }
    }

    private RuleSet emptyRuleSet(String hypothesis) {
        return new LoadedRuleSet(hypothesis, List.of(), List.of(), List.of(), 10, 5);
    }

    @SuppressWarnings("unchecked")
    private List<Rule> parseRules(List<Map<String, Object>> rulesData, RuleType type) {
        List<Rule> rules = new ArrayList<>();
        for (Map<String, Object> rd : rulesData) {
            String id = (String) rd.get("id");
            String desc = (String) rd.get("description");
            int weight = ((Number) rd.getOrDefault("weight", 1)).intValue();
            if (type == RuleType.EXCLUSION) {
                weight = -Math.abs(weight);
            }
            Map<String, Object> match = (Map<String, Object>) rd.getOrDefault("match", Map.of());
            Map<String, String> messages = (Map<String, String>) rd.getOrDefault("messages", Map.of());
            rules.add(new YamlRule(id, desc, type, weight, match, messages));
        }
        return rules;
    }

    private record LoadedRuleSet(
        String hypothesis,
        List<Rule> required,
        List<Rule> supporting,
        List<Rule> exclusion,
        int minSupported,
        int minPartial
    ) implements RuleSet {
        @Override public String getHypothesisName() { return hypothesis; }
        @Override public List<Rule> getRequiredRules() { return required; }
        @Override public List<Rule> getSupportingRules() { return supporting; }
        @Override public List<Rule> getExclusionRules() { return exclusion; }
        @Override public int getMinSupportedScore() { return minSupported; }
        @Override public int getMinPartiallySupportedScore() { return minPartial; }
    }

    private static class YamlRule extends Rule.BaseRule {
        private final Map<String, Object> match;
        private final Map<String, String> messages;

        YamlRule(String id, String description, RuleType type, int weight,
                 Map<String, Object> match, Map<String, String> messages) {
            super(id, description, type, weight);
            this.match = match;
            this.messages = messages;
        }

        @Override
        @SuppressWarnings("unchecked")
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            Object signalTypeRaw = match.get("signalType");
            Object signalNameRaw = match.get("signalName");
            Object signalValueRaw = match.get("signalValue");
            String condition = (String) match.getOrDefault("condition", "EQUALS");
            boolean caseInsensitive = "CASE_INSENSITIVE".equalsIgnoreCase(
                (String) match.getOrDefault("matchType", ""));

            List<String> signalTypes = toStringList(signalTypeRaw);
            List<String> signalNames = toStringList(signalNameRaw);

            List<Signal> matched = new ArrayList<>();
            for (Signal signal : signals) {
                boolean typeMatch = signalTypes.isEmpty() ||
                    signalTypes.stream().anyMatch(t -> t.equalsIgnoreCase(signal.getType().name()));
                boolean nameMatch = signalNames.isEmpty() ||
                    signalNames.stream().anyMatch(n -> caseInsensitive ?
                        n.equalsIgnoreCase(signal.getName()) : n.equals(signal.getName()));

                if (!typeMatch || !nameMatch) continue;

                boolean valueMatch = evaluateCondition(signal, condition, signalValueRaw,
                    (Number) match.get("threshold"), caseInsensitive);

                if (valueMatch) {
                    matched.add(signal);
                }
            }

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(this, matched,
                    messages.getOrDefault("success", "Rule matched"));
            }
            return RuleEvaluationResult.failed(this,
                messages.getOrDefault("failure", "Rule did not match"));
        }

        private boolean evaluateCondition(Signal signal, String condition,
                                          Object expectedValue, Number threshold,
                                          boolean caseInsensitive) {
            switch (condition.toUpperCase()) {
                case "EQUALS":
                    if (expectedValue == null) return true;
                    List<String> expectedValues = toStringList(expectedValue);
                    String actual = signal.getValueAsString();
                    return expectedValues.stream().anyMatch(ev ->
                        caseInsensitive ? ev.equalsIgnoreCase(actual) : ev.equals(actual));
                case "CONTAINS":
                    if (expectedValue == null) return true;
                    String actualStr = signal.getValueAsString();
                    if (actualStr == null) return false;
                    String expected = expectedValue.toString();
                    return caseInsensitive ?
                        actualStr.toLowerCase().contains(expected.toLowerCase()) :
                        actualStr.contains(expected);
                case "GREATER_THAN":
                    if (threshold == null) return false;
                    return signal.getValueAsDouble()
                        .map(v -> v > threshold.doubleValue())
                        .orElse(false);
                default:
                    return false;
            }
        }

        @SuppressWarnings("unchecked")
        private static List<String> toStringList(Object value) {
            if (value == null) return List.of();
            if (value instanceof List) return ((List<Object>) value).stream()
                .map(Object::toString).collect(Collectors.toList());
            return List.of(value.toString());
        }
    }
}
