package com.causa.rules.yaml;

import com.causa.common.logging.CausaLogger;
import com.causa.core.services.rules.RuleSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML Rule Set Registry.
 *
 * <p>Central registry for all YAML-based rule sets.
 * Provides thread-safe access to dynamically loaded rule sets.
 *
 * @since 1.0.0
 */
@ApplicationScoped
public class YamlRuleSetRegistry {

    private static final CausaLogger log = CausaLogger.getLogger(YamlRuleSetRegistry.class);

    @Inject
    YamlRuleSetLoader loader;

    private final Map<String, RuleSet> ruleSets = new ConcurrentHashMap<>();

    /**
     * Load all rule sets from YAML files.
     */
    public void loadAllRuleSets() {
        Map<String, YamlRuleSetDefinition> definitions = loader.loadAllRuleSets();

        for (Map.Entry<String, YamlRuleSetDefinition> entry : definitions.entrySet()) {
            String hypothesis = entry.getKey();
            YamlRuleSetDefinition definition = entry.getValue();

            RuleSet ruleSet = new YamlBasedRuleSet(definition);
            ruleSets.put(hypothesis, ruleSet);

            log.debug("Registered YAML rule set")
                .field("hypothesis", hypothesis)
                .field("requiredRules", ruleSet.getRequiredRules().size())
                .field("supportingRules", ruleSet.getSupportingRules().size())
                .field("exclusionRules", ruleSet.getExclusionRules().size())
                .log();
        }
    }

    /**
     * Register a single rule set.
     */
    public void registerRuleSet(String hypothesis, RuleSet ruleSet) {
        ruleSets.put(hypothesis, ruleSet);

        log.info("Rule set registered/updated")
            .field("hypothesis", hypothesis)
            .log();
    }

    /**
     * Get a rule set by hypothesis name.
     */
    public Optional<RuleSet> getRuleSet(String hypothesis) {
        return Optional.ofNullable(ruleSets.get(hypothesis));
    }

    /**
     * Get all registered rule sets.
     */
    public Map<String, RuleSet> getAllRuleSets() {
        return new HashMap<>(ruleSets);
    }

    /**
     * Get all hypothesis names.
     */
    public Set<String> getAllHypotheses() {
        return new HashSet<>(ruleSets.keySet());
    }

    /**
     * Check if a rule set exists for a hypothesis.
     */
    public boolean hasRuleSet(String hypothesis) {
        return ruleSets.containsKey(hypothesis);
    }

    /**
     * Clear all registered rule sets.
     */
    public void clear() {
        ruleSets.clear();
    }
}
