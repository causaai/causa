package com.causa.api.validators;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.common.exceptions.ConfigException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/**
 * Validates external-config and LLM-config upsert requests.
 *
 * <p>Required fields per platform / auth-type are declared in {@link #EXTERNAL_RULES}
 * and {@link #LLM_RULES}. Adding a new platform = one entry in the relevant map.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class ConfigRequestValidator {

    /**
     * Declares which top-level and auth-config fields are required for an external platform.
     *
     * @param needsName          whether {@code name} must be non-blank
     * @param needsUrl           whether {@code url} must be non-blank
     * @param requiredAuthFields auth_config field names that must be non-blank
     * @param requiredAdditional additional_config keys that must be present and non-blank
     */
    private record ExternalRules(
        boolean needsName,
        boolean needsUrl,
        List<String> requiredAuthFields,
        List<String> requiredAdditional
    ) {
        static ExternalRules of(boolean name, boolean url, List<String> auth, List<String> additional) {
            return new ExternalRules(name, url, auth, additional);
        }
    }

    /**
     * Declares which LLM request fields are required for a given auth type.
     *
     * @param requiredAuthFields  auth_config field names that must be non-blank
     * @param needsHeaders        whether auth_config.headers must be a non-empty map
     */
    private record LlmRules(List<String> requiredAuthFields, boolean needsHeaders) {
        static LlmRules of(List<String> auth) {
            return new LlmRules(auth, false);
        }
        static LlmRules headers() {
            return new LlmRules(List.of(), true);
        }
    }

    // -------------------------------------------------------------------------
    // Rules tables — one entry per platform / auth-type
    // -------------------------------------------------------------------------

    private static final Map<String, ExternalRules> EXTERNAL_RULES = Map.of(
        // Observability — name + url required
        "DATADOG", ExternalRules.of(true, true,  List.of("apiKey", "appKey"), List.of()),
        "INSTANA", ExternalRules.of(true, true,  List.of("token"),            List.of()),
        "OTHER",   ExternalRules.of(true, true,  List.of("token"),            List.of()),
        // Integrations — name required; url required for Slack (incoming webhook URL),
        // optional for Jira and GitHub (url is nullable in schema)
        "SLACK",   ExternalRules.of(true, true,  List.of("token"),            List.of("channel")),
        "JIRA",    ExternalRules.of(true, false, List.of("username", "token"), List.of("projectName")),
        "GITHUB",  ExternalRules.of(true, false, List.of("token"),             List.of("ownerRepo"))
    );

    private static final Map<String, LlmRules> LLM_RULES = Map.of(
        "API_KEY",        LlmRules.of(List.of("apiKey")),
        "SA_JSON_KEY",    LlmRules.of(List.of("credentialsJson")),
        "CUSTOM_HEADERS", LlmRules.headers()
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates an external-config upsert request for the given platform.
     *
     * @param platform platform name, e.g. {@code "DATADOG"} or {@code "datadog"} (normalised internally)
     * @throws ConfigException if the platform is unknown or any required field is missing
     */
    public void validateExternal(String platform, ExternalConfigRequest request) {
        if (platform == null || platform.isBlank()) {
            throw new ConfigException("platform must not be null or blank", "VALIDATION_ERROR");
        }
        ExternalRules rules = EXTERNAL_RULES.get(platform.toUpperCase());
        if (rules == null) {
            throw new ConfigException("No validator registered for platform: " + platform, "UNKNOWN_PLATFORM");
        }
        if (request == null) {
            throw new ConfigException("Request body is required for " + platform, "VALIDATION_ERROR");
        }
        if (rules.needsName()) {
            requireNonBlank(request.getName(), "name is required for " + platform);
        }
        if (rules.needsUrl()) {
            requireNonBlank(request.getUrl(), "url is required for " + platform);
        }
        if (!rules.requiredAuthFields().isEmpty()) {
            if (request.getAuthConfig() == null) {
                throw new ConfigException("auth_config is required for " + platform, "VALIDATION_ERROR");
            }
            for (String field : rules.requiredAuthFields()) {
                requireNonBlank(authField(request, field), "auth_config." + field + " is required for " + platform);
            }
        }
        for (String key : rules.requiredAdditional()) {
            Object val = request.getAdditionalConfig() != null ? request.getAdditionalConfig().get(key) : null;
            if (val == null || val.toString().isBlank()) {
                throw new ConfigException("additional_config." + key + " is required for " + platform, "VALIDATION_ERROR");
            }
        }
    }

    /**
     * Validates an LLM-config upsert request for the given auth type.
     *
     * @param authType auth type, e.g. {@code "API_KEY"} or {@code "api_key"} (normalised internally)
     * @throws ConfigException if the auth type is unknown or any required field is missing
     */
    public void validateLlm(String authType, LlmConfigRequest request) {
        if (authType == null || authType.isBlank()) {
            throw new ConfigException("auth_config.authType must not be null or blank", "VALIDATION_ERROR");
        }
        LlmRules rules = LLM_RULES.get(authType.toUpperCase());
        if (rules == null) {
            throw new ConfigException("No validator registered for auth type: " + authType, "UNKNOWN_AUTH_TYPE");
        }
        if (request == null) {
            throw new ConfigException("Request body is required for auth type " + authType, "VALIDATION_ERROR");
        }
        requireNonBlank(request.getUrl(), "url is required for " + authType);
        if (request.getModels() == null || request.getModels().isEmpty()) {
            throw new ConfigException("models must not be empty for " + authType, "VALIDATION_ERROR");
        }
        if (request.getAuthConfig() == null) {
            throw new ConfigException("auth_config is required for " + authType, "VALIDATION_ERROR");
        }
        for (String field : rules.requiredAuthFields()) {
            requireNonBlank(authField(request, field), "auth_config." + field + " is required for " + authType);
        }
        if (rules.needsHeaders()) {
            if (request.getAuthConfig().headers() == null || request.getAuthConfig().headers().isEmpty()) {
                throw new ConfigException("auth_config.headers must not be empty for " + authType, "VALIDATION_ERROR");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConfigException(message, "VALIDATION_ERROR");
        }
    }

    /** Reflectively resolves an {@link com.causa.core.domain.AuthConfig} field by name. */
    private static String authField(ExternalConfigRequest request, String field) {
        return resolveAuthField(request.getAuthConfig(), field);
    }

    private static String authField(LlmConfigRequest request, String field) {
        return resolveAuthField(request.getAuthConfig(), field);
    }

    private static String resolveAuthField(com.causa.core.domain.AuthConfig auth, String field) {
        return switch (field) {
            case "apiKey"           -> auth.apiKey();
            case "appKey"           -> auth.appKey();
            case "token"            -> auth.token();
            case "credentialsJson"  -> auth.credentialsJson();
            case "username"         -> auth.username();
            case "password"         -> auth.password();
            default -> null;
        };
    }
}
