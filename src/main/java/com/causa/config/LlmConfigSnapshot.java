package com.causa.config;

import java.util.Map;

/**
 * LLM Configuration Snapshot
 *
 * <p>Immutable typed view of LLM-related configuration from the runtime cache.
 * Reads 15 LLM config keys from AppConfig and provides typed getters with sensible defaults.
 *
 * <p>This snapshot is built on-demand from the current cache state. Changes to the cache
 * do not affect already-created snapshots.
 *
 * @since 0.0.1
 */
public final class LlmConfigSnapshot {

    private final Map<String, String> config;

    public LlmConfigSnapshot(Map<String, String> config) {
        this.config = Map.copyOf(config);
    }

    public String getProvider() {
        return config.getOrDefault("LLM_PROVIDER", "");
    }

    public String getModelName() {
        return config.getOrDefault("LLM_MODEL_NAME", "");
    }

    public String getBaseUrl() {
        return config.getOrDefault("LLM_BASE_URL", "");
    }

    public String getAuthType() {
        return config.getOrDefault("LLM_AUTH_TYPE", "");
    }

    public String getCustomHeaders() {
        return config.getOrDefault("LLM_CUSTOM_HEADERS", "{}");
    }

    public double getTemperature() {
        String value = config.get("LLM_TEMPERATURE");
        if (value == null || value.isBlank()) {
            return 0.1;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.1;
        }
    }

    public int getMaxTokens() {
        String value = config.get("LLM_MAX_TOKENS");
        if (value == null || value.isBlank()) {
            return 8192;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 8192;
        }
    }

    public String getApiKey() {
        return config.getOrDefault("LLM_API_KEY", "");
    }

    public int getTimeoutSeconds() {
        String value = config.get("LLM_TIMEOUT_SECONDS");
        if (value == null || value.isBlank()) {
            return 180;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 180;
        }
    }

    public int getChatMemorySize() {
        String value = config.get("LLM_CHAT_MEMORY_SIZE");
        if (value == null || value.isBlank()) {
            return 10;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public String getVertexProjectId() {
        return config.getOrDefault("VERTEX_PROJECT_ID", "");
    }

    public String getVertexLocation() {
        return config.getOrDefault("VERTEX_LOCATION", "");
    }

    public String getBobShellPath() {
        return config.getOrDefault("BOB_SHELL_PATH", "bob");
    }

    public String getGoogleApplicationCredentials() {
        return config.getOrDefault("GOOGLE_APPLICATION_CREDENTIALS", "");
    }

    public boolean isSkillsEnabled() {
        String value = config.getOrDefault("SKILLS_ENABLED", "false");
        return Boolean.parseBoolean(value);
    }
}
