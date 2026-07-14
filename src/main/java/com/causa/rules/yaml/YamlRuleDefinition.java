package com.causa.rules.yaml;

import java.util.List;
import java.util.Map;

/**
 * YAML-based Rule Definition.
 *
 * <p>Represents a single rule defined in YAML format.
 *
 * @since 1.0.0
 */
public class YamlRuleDefinition {

    private String id;
    private String description;
    private int weight = 1;
    private MatchCriteria match;
    private Messages messages;
    private Map<String, Object> metadata;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public MatchCriteria getMatch() {
        return match;
    }

    public void setMatch(MatchCriteria match) {
        this.match = match;
    }

    public Messages getMessages() {
        return messages;
    }

    public void setMessages(Messages messages) {
        this.messages = messages;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Signal matching criteria.
     */
    public static class MatchCriteria {
        private Object signalType;  // String or List<String>
        private Object signalName;  // String or List<String>
        private Object signalValue; // Any type
        private String condition;   // EQUALS, GREATER_THAN, LESS_THAN, CONTAINS, REGEX
        private String matchType;   // CASE_SENSITIVE, CASE_INSENSITIVE, EXACT
        private Double threshold;   // For numeric comparisons

        public Object getSignalType() {
            return signalType;
        }

        public void setSignalType(Object signalType) {
            this.signalType = signalType;
        }

        public Object getSignalName() {
            return signalName;
        }

        public void setSignalName(Object signalName) {
            this.signalName = signalName;
        }

        public Object getSignalValue() {
            return signalValue;
        }

        public void setSignalValue(Object signalValue) {
            this.signalValue = signalValue;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public String getMatchType() {
            return matchType;
        }

        public void setMatchType(String matchType) {
            this.matchType = matchType;
        }

        public Double getThreshold() {
            return threshold;
        }

        public void setThreshold(Double threshold) {
            this.threshold = threshold;
        }
    }

    /**
     * Success/failure messages.
     */
    public static class Messages {
        private String success;
        private String failure;

        public String getSuccess() {
            return success;
        }

        public void setSuccess(String success) {
            this.success = success;
        }

        public String getFailure() {
            return failure;
        }

        public void setFailure(String failure) {
            this.failure = failure;
        }
    }
}
