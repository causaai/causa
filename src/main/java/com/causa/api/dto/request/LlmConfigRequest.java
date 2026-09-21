package com.causa.api.dto.request;

import com.causa.core.domain.AuthConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Request body for {@code PUT /api/v1/settings/llm/{provider}}.
 *
 * <p>{@code provider} is taken from the URL path, not the body.
 * {@code authType} inside {@code authConfig} determines which credential fields are validated
 * and encrypted: {@code API_KEY} → apiKey; {@code SA_JSON_KEY} → credentialsJson;
 * {@code CUSTOM_HEADERS} → headers.
 * When {@code isActive} is {@code true}, all other providers are deactivated in the same transaction.
 *
 * @since 0.0.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmConfigRequest {

    @JsonProperty("url")
    private String url;

    @JsonProperty("models")
    private List<String> models;

    @JsonProperty("temperature")
    private BigDecimal temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("timeout_ms")
    private Integer timeoutMs;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("auth_config")
    private AuthConfig authConfig;

    @JsonProperty("additional_config")
    private Map<String, Object> additionalConfig;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = models;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public AuthConfig getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }

    public Map<String, Object> getAdditionalConfig() {
        return additionalConfig;
    }

    public void setAdditionalConfig(Map<String, Object> additionalConfig) {
        this.additionalConfig = additionalConfig;
    }
}
