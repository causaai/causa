package com.causa.core.services.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Signal - Normalized Evidence from Observability Data.
 *
 * <p>Represents a structured piece of evidence extracted from logs, metrics,
 * events, traces, or other diagnostic sources.
 *
 * <p>Signals are the input to rule evaluation. Each signal has:
 * <ul>
 *   <li><strong>type:</strong> Category (KUBERNETES_EVENT, METRIC, LOG, etc.)</li>
 *   <li><strong>name:</strong> Signal identifier (e.g., "pod.status", "heap.usage.trend")</li>
 *   <li><strong>value:</strong> Signal value (e.g., "OOMKilled", "INCREASING", "137")</li>
 *   <li><strong>metadata:</strong> Additional context (source, timestamp, etc.)</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class Signal {

    public enum SignalType {
        KUBERNETES_EVENT,
        POD_STATUS,
        CONTAINER_STATUS,
        METRIC,
        LOG_PATTERN,
        TRACE,
        KRUIZE_RECOMMENDATION,
        JVM_ANALYSIS,
        CRYOSTAT_ANALYSIS
    }

    private final SignalType type;
    private final String name;
    private final Object value;
    private final Map<String, Object> metadata;

    private Signal(SignalType type, String name, Object value, Map<String, Object> metadata) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public static Builder builder(SignalType type, String name) {
        return new Builder(type, name);
    }

    public SignalType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public String getValueAsString() {
        return value != null ? value.toString() : null;
    }

    public Optional<Integer> getValueAsInt() {
        if (value instanceof Integer) {
            return Optional.of((Integer) value);
        }
        if (value instanceof String) {
            try {
                return Optional.of(Integer.parseInt((String) value));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Optional<Double> getValueAsDouble() {
        if (value instanceof Double) {
            return Optional.of((Double) value);
        }
        if (value instanceof Number) {
            return Optional.of(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            try {
                return Optional.of(Double.parseDouble((String) value));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Optional<Boolean> getValueAsBoolean() {
        if (value instanceof Boolean) {
            return Optional.of((Boolean) value);
        }
        if (value instanceof String) {
            return Optional.of(Boolean.parseBoolean((String) value));
        }
        return Optional.empty();
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public Optional<Object> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    @Override
    public String toString() {
        return String.format("Signal[type=%s, name=%s, value=%s]", type, name, value);
    }

    public static class Builder {
        private final SignalType type;
        private final String name;
        private Object value;
        private Map<String, Object> metadata = new HashMap<>();

        private Builder(SignalType type, String name) {
            this.type = type;
            this.name = name;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public Signal build() {
            return new Signal(type, name, value, metadata);
        }
    }
}
