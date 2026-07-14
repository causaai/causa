package com.causa.rules.yaml;

import com.causa.core.services.rules.Rule;
import com.causa.core.services.rules.RuleEvaluationResult;
import com.causa.core.services.rules.RuleType;
import com.causa.core.services.rules.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Dynamic Rule Evaluator.
 *
 * <p>Evaluates rules dynamically based on YAML definitions.
 * Converts YAML rule definitions into executable rule logic.
 *
 * @since 1.0.0
 */
public class DynamicRuleEvaluator extends Rule.BaseRule {

    private final YamlRuleDefinition definition;

    public DynamicRuleEvaluator(YamlRuleDefinition definition, RuleType type) {
        super(
            definition.getId(),
            definition.getDescription(),
            type,
            definition.getWeight()
        );
        this.definition = definition;
    }

    @Override
    public RuleEvaluationResult evaluate(List<Signal> signals) {
        List<Signal> matched = new ArrayList<>();

        // Extract match criteria
        YamlRuleDefinition.MatchCriteria criteria = definition.getMatch();
        if (criteria == null) {
            return RuleEvaluationResult.failed(this, "No match criteria defined");
        }

        // Filter signals based on criteria
        for (Signal signal : signals) {
            if (matchesSignal(signal, criteria)) {
                matched.add(signal);
            }
        }

        // Determine result
        if (!matched.isEmpty()) {
            String successMsg = definition.getMessages() != null && definition.getMessages().getSuccess() != null
                ? definition.getMessages().getSuccess()
                : "Rule matched " + matched.size() + " signal(s)";

            return RuleEvaluationResult.passed(this, matched, successMsg);
        } else {
            String failureMsg = definition.getMessages() != null && definition.getMessages().getFailure() != null
                ? definition.getMessages().getFailure()
                : "No matching signals found";

            return RuleEvaluationResult.failed(this, failureMsg);
        }
    }

    /**
     * Check if a signal matches the YAML criteria.
     */
    private boolean matchesSignal(Signal signal, YamlRuleDefinition.MatchCriteria criteria) {
        // Match signal type
        if (criteria.getSignalType() != null && !matchesSignalType(signal, criteria.getSignalType())) {
            return false;
        }

        // Match signal name
        if (criteria.getSignalName() != null && !matchesSignalName(signal, criteria.getSignalName())) {
            return false;
        }

        // Match signal value
        if (criteria.getSignalValue() != null || criteria.getThreshold() != null) {
            return matchesSignalValue(signal, criteria);
        }

        return true;
    }

    /**
     * Match signal type (supports single value or list).
     */
    private boolean matchesSignalType(Signal signal, Object typePattern) {
        if (typePattern instanceof String) {
            return signal.getType().name().equals(typePattern);
        } else if (typePattern instanceof List) {
            List<?> types = (List<?>) typePattern;
            return types.stream().anyMatch(t -> signal.getType().name().equals(t.toString()));
        }
        return false;
    }

    /**
     * Match signal name (supports single value or list).
     */
    private boolean matchesSignalName(Signal signal, Object namePattern) {
        if (namePattern instanceof String) {
            return signal.getName().equals(namePattern);
        } else if (namePattern instanceof List) {
            List<?> names = (List<?>) namePattern;
            return names.stream().anyMatch(n -> signal.getName().equals(n.toString()));
        }
        return false;
    }

    /**
     * Match signal value based on condition.
     */
    private boolean matchesSignalValue(Signal signal, YamlRuleDefinition.MatchCriteria criteria) {
        String condition = criteria.getCondition() != null ? criteria.getCondition() : "EQUALS";
        String matchType = criteria.getMatchType() != null ? criteria.getMatchType() : "EXACT";

        switch (condition.toUpperCase()) {
            case "EQUALS":
                return matchesEquals(signal, criteria.getSignalValue(), matchType);

            case "GREATER_THAN":
                if (criteria.getThreshold() != null) {
                    return signal.getValueAsDouble().orElse(0.0) > criteria.getThreshold();
                }
                return false;

            case "LESS_THAN":
                if (criteria.getThreshold() != null) {
                    return signal.getValueAsDouble().orElse(0.0) < criteria.getThreshold();
                }
                return false;

            case "GREATER_THAN_OR_EQUAL":
                if (criteria.getThreshold() != null) {
                    return signal.getValueAsDouble().orElse(Double.MIN_VALUE) >= criteria.getThreshold();
                }
                return false;

            case "LESS_THAN_OR_EQUAL":
                if (criteria.getThreshold() != null) {
                    return signal.getValueAsDouble().orElse(Double.MAX_VALUE) <= criteria.getThreshold();
                }
                return false;

            case "CONTAINS":
                String signalStr = signal.getValueAsString();
                String valueStr = criteria.getSignalValue().toString();
                if ("CASE_INSENSITIVE".equalsIgnoreCase(matchType)) {
                    return signalStr != null && signalStr.toLowerCase().contains(valueStr.toLowerCase());
                }
                return signalStr != null && signalStr.contains(valueStr);

            case "REGEX":
                String regexPattern = criteria.getSignalValue().toString();
                String signalValue = signal.getValueAsString();
                if (signalValue == null) {
                    return false;
                }
                Pattern pattern = Pattern.compile(regexPattern);
                return pattern.matcher(signalValue).find();

            default:
                return matchesEquals(signal, criteria.getSignalValue(), matchType);
        }
    }

    /**
     * Match equals with different match types.
     */
    private boolean matchesEquals(Signal signal, Object expectedValue, String matchType) {
        if (expectedValue == null) {
            return true;
        }

        Object signalValue = signal.getValue();

        // Numeric comparison
        if (expectedValue instanceof Number) {
            return signal.getValueAsDouble().orElse(Double.NaN)
                .equals(((Number) expectedValue).doubleValue());
        }

        // Integer comparison
        if (signalValue instanceof Integer || expectedValue.toString().matches("\\d+")) {
            try {
                int expected = Integer.parseInt(expectedValue.toString());
                return signal.getValueAsInt().orElse(Integer.MIN_VALUE) == expected;
            } catch (NumberFormatException e) {
                // Fall through to string comparison
            }
        }

        // String comparison
        String signalStr = signal.getValueAsString();
        String expectedStr = expectedValue.toString();

        if ("CASE_INSENSITIVE".equalsIgnoreCase(matchType)) {
            return signalStr != null && signalStr.equalsIgnoreCase(expectedStr);
        }

        return signalStr != null && signalStr.equals(expectedStr);
    }
}
