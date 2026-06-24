package com.causa.observability.integration.dto;

/**
 * Supported observability provider types
 *
 * @since 1.0.0
 */
public enum ProviderType {
    DATADOG("datadog"),
    GRAFANA("grafana"),
    INSTANA("instana"),
    DYNATRACE("dynatrace");

    private final String value;

    ProviderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProviderType fromValue(String value) {
        for (ProviderType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown provider type: " + value);
    }
}

// Made with Bob
