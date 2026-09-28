package com.causa.api.dto.request;

import com.causa.core.domain.AuthConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request body for {@code PUT /api/v1/settings/observability/{platform}}
 * and {@code PUT /api/v1/settings/integrations/{platform}}.
 *
 * <p>{@code platform} is taken from the URL path, not the body.
 * {@code authConfig} carries credentials; sensitive fields are encrypted before storage.
 * {@code additionalConfig} holds platform-specific behavioural fields
 * (e.g. {@code channel} for Slack, {@code projectName} and {@code issueType} for Jira).
 *
 * @since 0.0.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalConfigRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("url")
    private String url;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("auth_config")
    private AuthConfig authConfig;

    @JsonProperty("additional_config")
    private Map<String, Object> additionalConfig;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
