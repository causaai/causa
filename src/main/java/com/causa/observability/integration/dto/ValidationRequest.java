package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic request for validating provider credentials
 * Supports all observability providers through a flexible config map
 *
 * Example for Datadog:
 * {
 *   "config": {
 *     "site": "us5.datadoghq.com",
 *     "apiKey": "xxx",
 *     "appKey": "yyy"
 *   }
 * }
 *
 * Example for Grafana:
 * {
 *   "config": {
 *     "url": "https://grafana.example.com",
 *     "token": "xxx"
 *   }
 * }
 *
 * @since 1.0.0
 */
public class ValidationRequest {

    @JsonProperty("config")
    private Map<String, Object> config = new HashMap<>();

    public ValidationRequest() {
    }

    public ValidationRequest(Map<String, Object> config) {
        this.config = config;
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
