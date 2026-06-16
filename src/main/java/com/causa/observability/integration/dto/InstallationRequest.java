package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic request for installing an integration
 * Supports all observability providers through a flexible config map
 *
 * Example for Datadog:
 * {
 *   "provider": "datadog",
 *   "config": {
 *     "site": "us5.datadoghq.com",
 *     "apiKey": "xxx",
 *     "appKey": "yyy"
 *   }
 * }
 *
 * @since 1.0.0
 */
public class InstallationRequest {

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("config")
    private Map<String, Object> config = new HashMap<>();

    public InstallationRequest() {
    }

    public InstallationRequest(String provider, Map<String, Object> config) {
        this.provider = provider;
        this.config = config;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public String getConfigValue(String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    public void setConfigValue(String key, Object value) {
        config.put(key, value);
    }
}

// Made with Bob
