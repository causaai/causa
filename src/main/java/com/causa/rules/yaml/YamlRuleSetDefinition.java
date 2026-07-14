package com.causa.rules.yaml;

import java.util.List;
import java.util.Map;

/**
 * YAML-based Rule Set Definition.
 *
 * <p>Represents a complete rule set defined in YAML format.
 * Allows zero-code creation of new rule sets by just adding YAML files.
 *
 * @since 1.0.0
 */
public class YamlRuleSetDefinition {

    private String hypothesis;
    private String name;
    private String description;
    private ThresholdConfig thresholds;
    private List<YamlRuleDefinition> required;
    private List<YamlRuleDefinition> supporting;
    private List<YamlRuleDefinition> exclusion;
    private Map<String, Object> metadata;

    // Getters and setters
    public String getHypothesis() {
        return hypothesis;
    }

    public void setHypothesis(String hypothesis) {
        this.hypothesis = hypothesis;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ThresholdConfig getThresholds() {
        return thresholds;
    }

    public void setThresholds(ThresholdConfig thresholds) {
        this.thresholds = thresholds;
    }

    public List<YamlRuleDefinition> getRequired() {
        return required;
    }

    public void setRequired(List<YamlRuleDefinition> required) {
        this.required = required;
    }

    public List<YamlRuleDefinition> getSupporting() {
        return supporting;
    }

    public void setSupporting(List<YamlRuleDefinition> supporting) {
        this.supporting = supporting;
    }

    public List<YamlRuleDefinition> getExclusion() {
        return exclusion;
    }

    public void setExclusion(List<YamlRuleDefinition> exclusion) {
        this.exclusion = exclusion;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Score threshold configuration.
     */
    public static class ThresholdConfig {
        private int minSupportedScore = 10;
        private int minPartiallySupportedScore = 5;

        public int getMinSupportedScore() {
            return minSupportedScore;
        }

        public void setMinSupportedScore(int minSupportedScore) {
            this.minSupportedScore = minSupportedScore;
        }

        public int getMinPartiallySupportedScore() {
            return minPartiallySupportedScore;
        }

        public void setMinPartiallySupportedScore(int minPartiallySupportedScore) {
            this.minPartiallySupportedScore = minPartiallySupportedScore;
        }
    }
}
