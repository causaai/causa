package com.causa.api.dto.response;

import com.causa.common.constants.ConfigConstants;

/**
 * Config Response DTO
 *
 * <p>Response format for individual configuration entries in {@code GET /api/v1/configs}.
 *
 * @param key       the configuration key
 * @param value     the configuration value (masked if sensitive)
 * @param category  the category (llm, alerts, cluster)
 * @param encrypted whether the value is encrypted in the database
 * @since 0.0.1
 */
public record ConfigResponse(String key, String value, String category, boolean encrypted) {

    /**
     * Factory method: auto-detects category and sensitivity from the key.
     * Masks sensitive values.
     *
     * @param key   the config key
     * @param value the config value
     * @return response DTO with masked sensitive values
     */
    public static ConfigResponse of(String key, String value) {
        String category = ConfigConstants.categoryOf(key);
        boolean isSensitive = ConfigConstants.isSensitive(key);
        String displayValue = isSensitive ? ConfigConstants.MASKED_VALUE : value;
        return new ConfigResponse(key, displayValue, category, isSensitive);
    }

    /**
     * Factory method with explicit encrypted flag (used by service layer).
     *
     * @param key       the config key
     * @param value     the config value
     * @param encrypted whether the value is encrypted
     * @return response DTO
     */
    public static ConfigResponse of(String key, String value, boolean encrypted) {
        String category = ConfigConstants.categoryOf(key);
        String displayValue = encrypted ? ConfigConstants.MASKED_VALUE : value;
        return new ConfigResponse(key, displayValue, category, encrypted);
    }
}
