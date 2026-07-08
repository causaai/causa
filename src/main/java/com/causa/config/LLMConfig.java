package com.causa.config;

import com.causa.common.constants.ConfigConstants;

import java.util.Map;
import java.util.Optional;

/**
 * LLM Configuration Snapshot
 *
 * <p>Typed view of all LLM-related keys from the in-memory configuration cache.
 * Constructed by {@link AppConfig} on every call to {@link AppConfig#getLlmConfig()}
 * so callers always receive the current values from the DB-backed cache.
 *
 * <p>Keys mirror {@link ConfigConstants.LLM}.
 *
 * @since 0.0.1
 */
public final class LLMConfig {

    private final String provider;
    private final String modelName;
    private final String baseUrl;
    private final String authType;
    private final String customHeaders;
    private final String temperature;
    private final String maxTokens;
    private final String apiKey;
    private final String timeoutSeconds;
    private final String chatMemorySize;
    private final String vertexProjectId;
    private final String vertexLocation;
    private final String bobShellPath;
    private final String googleApplicationCredentials;

    LLMConfig(Map<String, String> cache) {
        this.provider                    = cache.get(ConfigConstants.LLM.PROVIDER);
        this.modelName                   = cache.get(ConfigConstants.LLM.MODEL_NAME);
        this.baseUrl                     = cache.get(ConfigConstants.LLM.BASE_URL);
        this.authType                    = cache.get(ConfigConstants.LLM.AUTH_TYPE);
        this.customHeaders               = cache.get(ConfigConstants.LLM.CUSTOM_HEADERS);
        this.temperature                 = cache.get(ConfigConstants.LLM.TEMPERATURE);
        this.maxTokens                   = cache.get(ConfigConstants.LLM.MAX_TOKENS);
        this.apiKey                      = cache.get(ConfigConstants.LLM.API_KEY);
        this.timeoutSeconds              = cache.get(ConfigConstants.LLM.TIMEOUT_SECONDS);
        this.chatMemorySize              = cache.get(ConfigConstants.LLM.CHAT_MEMORY_SIZE);
        this.vertexProjectId             = cache.get(ConfigConstants.LLM.VERTEX_PROJECT_ID);
        this.vertexLocation              = cache.get(ConfigConstants.LLM.VERTEX_LOCATION);
        this.bobShellPath                = cache.get(ConfigConstants.LLM.BOB_SHELL_PATH);
        this.googleApplicationCredentials = cache.get(ConfigConstants.LLM.GOOGLE_APPLICATION_CREDENTIALS);
    }

    /** LLM provider identifier — e.g. {@code vertex-ai-anthropic}, {@code anthropic}, {@code bob}. */
    public Optional<String> getProvider() {
        return Optional.ofNullable(provider);
    }

    /** Model name — e.g. {@code claude-sonnet-4-6}. */
    public Optional<String> getModelName() {
        return Optional.ofNullable(modelName);
    }

    /** Custom base URL — overrides provider default endpoints. */
    public Optional<String> getBaseUrl() {
        return Optional.ofNullable(baseUrl);
    }

    /** Authentication type — {@code API_KEY} or {@code ADC}. */
    public Optional<String> getAuthType() {
        return Optional.ofNullable(authType);
    }

    /** Custom HTTP headers as a raw JSON string. */
    public Optional<String> getCustomHeaders() {
        return Optional.ofNullable(customHeaders);
    }

    /** Sampling temperature; defaults to {@code 0.1} if not set. */
    public double getTemperature() {
        return temperature != null ? Double.parseDouble(temperature) : 0.1;
    }

    /** Maximum tokens to generate; defaults to {@code 8192} if not set. */
    public int getMaxTokens() {
        return maxTokens != null ? Integer.parseInt(maxTokens) : 8192;
    }

    /** API key for direct-API providers. */
    public Optional<String> getApiKey() {
        return Optional.ofNullable(apiKey);
    }

    /** Request timeout in seconds; defaults to {@code 180} if not set. */
    public int getTimeoutSeconds() {
        return timeoutSeconds != null ? Integer.parseInt(timeoutSeconds) : 180;
    }

    /** Chat memory size; defaults to {@code 10} if not set. */
    public int getChatMemorySize() {
        return chatMemorySize != null ? Integer.parseInt(chatMemorySize) : 10;
    }

    /** Google Cloud project ID for Vertex AI. */
    public Optional<String> getVertexProjectId() {
        return Optional.ofNullable(vertexProjectId);
    }

    /** Google Cloud region for Vertex AI — e.g. {@code us-east5}. */
    public Optional<String> getVertexLocation() {
        return Optional.ofNullable(vertexLocation);
    }

    /** BOB Shell executable path; defaults to {@code "bob"} if not set. */
    public String getBobShellPath() {
        return bobShellPath != null ? bobShellPath : "bob";
    }

    /**
     * Base64-encoded Google ADC JSON (personal ADC or service account key).
     * Decode with {@link java.util.Base64#getDecoder()} then pass to
     * {@code GoogleCredentials.fromStream()} — no file path, no pod mount.
     */
    public Optional<String> getGoogleApplicationCredentials() {
        return Optional.ofNullable(googleApplicationCredentials);
    }
}
