package com.causa.api.validators.impl;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.validators.ConfigValidator.ExternalConfigValidator;
import com.causa.common.exceptions.ConfigException;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Full request validator for the DATADOG observability platform.
 *
 * <p>Required fields:
 * <ul>
 *   <li>{@code name} — user-defined config label</li>
 *   <li>{@code url} — Datadog API endpoint (e.g. {@code https://api.datadoghq.com})</li>
 *   <li>{@code authConfig.apiKey} — ingest API key (encrypted before storage)</li>
 *   <li>{@code authConfig.appKey} — application key for read/query access (encrypted before storage)</li>
 * </ul>
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class DatadogConfigValidator implements ExternalConfigValidator {

    @Override
    public void validate(ExternalConfigRequest request) {
        if (request == null) {
            throw new ConfigException("Request body is required for DATADOG", "VALIDATION_ERROR");
        }
        requireNonBlank(request.getName(), "name is required for DATADOG");
        requireNonBlank(request.getUrl(),  "url is required for DATADOG");
        if (request.getAuthConfig() == null) {
            throw new ConfigException("auth_config is required for DATADOG", "VALIDATION_ERROR");
        }
        requireNonBlank(request.getAuthConfig().apiKey(), "auth_config.apiKey is required for DATADOG");
        requireNonBlank(request.getAuthConfig().appKey(), "auth_config.appKey is required for DATADOG");
    }
}
