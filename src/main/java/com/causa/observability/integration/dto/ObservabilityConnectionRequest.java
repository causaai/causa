package com.causa.observability.integration.dto;

import java.util.HashMap;
import java.util.Map;

import com.causa.observability.integration.dto.ProviderType;

/**
 * Request to connect/configure an observability platform integration
 */
public class ObservabilityConnectionRequest {

    private ProviderType provider;
    private Map<String, String> config;

    public ObservabilityConnectionRequest() {
        this.config = new HashMap<>();
    }

    public ProviderType getProvider() {
        return provider;
    }

    public void setProvider(ProviderType provider) {
        this.provider = provider;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    public String getConfigValue(String key) {
        return config != null ? config.get(key) : null;
    }

    public void setConfigValue(String key, String value) {
        if (config == null) {
            config = new HashMap<>();
        }
        config.put(key, value);
    }
}
