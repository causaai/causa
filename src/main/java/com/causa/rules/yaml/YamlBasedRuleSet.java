package com.causa.rules.yaml;

import com.causa.core.services.rules.Rule;
import com.causa.core.services.rules.RuleSet;
import com.causa.core.services.rules.RuleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * YAML-based Rule Set.
 *
 * <p>Converts a YAML rule set definition into a runtime RuleSet implementation.
 * This allows zero-code creation of new rule sets.
 *
 * @since 1.0.0
 */
public class YamlBasedRuleSet implements RuleSet {

    private final YamlRuleSetDefinition definition;
    private final List<Rule> requiredRules;
    private final List<Rule> supportingRules;
    private final List<Rule> exclusionRules;

    public YamlBasedRuleSet(YamlRuleSetDefinition definition) {
        this.definition = definition;

        // Convert YAML rules to executable rules
        this.requiredRules = convertRules(definition.getRequired(), RuleType.REQUIRED);
        this.supportingRules = convertRules(definition.getSupporting(), RuleType.SUPPORTING);
        this.exclusionRules = convertRules(definition.getExclusion(), RuleType.EXCLUSION);
    }

    @Override
    public String getHypothesisName() {
        return definition.getHypothesis();
    }

    @Override
    public List<Rule> getRequiredRules() {
        return requiredRules;
    }

    @Override
    public List<Rule> getSupportingRules() {
        return supportingRules;
    }

    @Override
    public List<Rule> getExclusionRules() {
        return exclusionRules;
    }

    @Override
    public int getMinSupportedScore() {
        return definition.getThresholds() != null
            ? definition.getThresholds().getMinSupportedScore()
            : 10;
    }

    @Override
    public int getMinPartiallySupportedScore() {
        return definition.getThresholds() != null
            ? definition.getThresholds().getMinPartiallySupportedScore()
            : 5;
    }

    /**
     * Convert YAML rule definitions to executable Rule objects.
     */
    private List<Rule> convertRules(List<YamlRuleDefinition> yamlRules, RuleType type) {
        if (yamlRules == null || yamlRules.isEmpty()) {
            return Collections.emptyList();
        }

        return yamlRules.stream()
            .map(yamlRule -> new DynamicRuleEvaluator(yamlRule, type))
            .collect(Collectors.toList());
    }

    /**
     * Get rule set metadata.
     */
    public String getName() {
        return definition.getName();
    }

    public String getDescription() {
        return definition.getDescription();
    }
}
