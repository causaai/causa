package com.causa.api.validators;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.common.exceptions.ConfigException;

/**
 * Base interface providing shared validation helpers for settings upserts.
 *
 * <p>Do not implement this interface directly. Implement one of the typed sub-interfaces:
 * <ul>
 *   <li>{@link ExternalConfigValidator} — for observability / integration platforms</li>
 *   <li>{@link LlmConfigValidator} — for LLM provider auth types</li>
 * </ul>
 *
 * @see ConfigValidatorRegistry
 * @since 0.0.4
 */
public interface ConfigValidator {

    default void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConfigException(message, "VALIDATION_ERROR");
        }
    }

    /**
     * Typed validator for external config upserts (observability / integrations).
     * One implementation per platform (e.g. DATADOG, INSTANA, SLACK).
     *
     * @throws ConfigException if any required field is missing or blank
     */
    interface ExternalConfigValidator extends ConfigValidator {
        void validate(ExternalConfigRequest request);
    }

    /**
     * Typed validator for LLM config upserts.
     * One implementation per auth type (e.g. API_KEY, SA_JSON_KEY, CUSTOM_HEADERS).
     *
     * @throws ConfigException if any required field is missing or blank
     */
    interface LlmConfigValidator extends ConfigValidator {
        void validate(LlmConfigRequest request);
    }
}
