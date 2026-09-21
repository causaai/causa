package com.causa.api.validators;

import com.causa.api.validators.ConfigValidator.ExternalConfigValidator;
import com.causa.api.validators.ConfigValidator.LlmConfigValidator;
import com.causa.api.validators.impl.DatadogConfigValidator;
import com.causa.common.exceptions.ConfigException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Registry that maps each platform / auth-type name to its typed {@link ConfigValidator}.
 *
 * <p>The service calls {@code registry.getExternal("DATADOG").validate(request)} — no if/switch needed.
 * Adding a new platform requires only a new {@link ExternalConfigValidator} implementation
 * and one entry in the map here. LLM auth types follow the same pattern via {@link LlmConfigValidator}.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class ConfigValidatorRegistry {

    private final Map<String, ExternalConfigValidator> externalValidators;
    private final Map<String, LlmConfigValidator>      llmValidators;

    @Inject
    public ConfigValidatorRegistry(DatadogConfigValidator datadog) {
        this.externalValidators = Map.of(
            "DATADOG", datadog
        );
        this.llmValidators = Map.of();
    }

    /**
     * Returns the external-config validator for the given platform name.
     *
     * @param platform enum name, e.g. {@code "DATADOG"}, {@code "SLACK"}
     * @throws ConfigException if no validator is registered for the platform
     */
    public ExternalConfigValidator getExternalConfigValidator(String platform) {
        ExternalConfigValidator validator = externalValidators.get(platform);
        if (validator == null) {
            throw new ConfigException("No validator registered for platform: " + platform, "UNKNOWN_PLATFORM");
        }
        return validator;
    }

    /**
     * Returns the LLM-config validator for the given auth type.
     *
     * @param authType enum name, e.g. {@code "API_KEY"}, {@code "SA_JSON_KEY"}
     * @throws ConfigException if no validator is registered for the auth type
     */
    public LlmConfigValidator getLlmConfigValidator(String authType) {
        LlmConfigValidator validator = llmValidators.get(authType);
        if (validator == null) {
            throw new ConfigException("No validator registered for auth type: " + authType, "UNKNOWN_AUTH_TYPE");
        }
        return validator;
    }
}
