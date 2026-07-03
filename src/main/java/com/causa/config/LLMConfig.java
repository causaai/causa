package com.causa.config;

import com.causa.common.utils.JsonUtils;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;

/**
 * LLM Configuration
 *
 * <p>Type-safe configuration for LLM integration via LangChain4J.
 * Maps to {@code causa.llm.*} in application.yml, which reads from {@code LLM_*} environment variables.
 *
 * <p>All properties support runtime ENV variable overrides.
 * All required string fields are {@link Optional} so the application starts cleanly even when
 * LLM environment variables are not set. Missing values are handled at the usage site with
 * a clean "LLM config not available" message instead of a startup exception.
 *
 * @since 0.0.1
 */
@ConfigMapping(prefix = "causa.llm")
public interface LLMConfig {

    /**
     * LLM provider to use (anthropic, vertex-ai-anthropic, openai, ollama).
     *
     * @return the provider name, or empty if not configured
     */
    @WithName("provider")
    Optional<String> provider();

    /**
     * Model name to use for LLM requests.
     *
     * @return the model name, or empty if not configured
     */
    @WithName("model-name")
    Optional<String> modelName();

    /**
     * Base URL for the LLM API. Overrides default cloud endpoints.
     *
     * @return the base URL, or empty to use provider defaults
     */
    @WithName("base-url")
    Optional<String> baseUrl();

    /**
     * Authentication type (API_KEY, BEARER_TOKEN, MTLS, NONE).
     *
     * @return the authentication type, or empty if not specified
     */
    @WithName("auth-type")
    Optional<String> authType();

    /**
     * Custom HTTP headers as a JSON object for gateway routing or proxy handshakes.
     * <p>
     * Expects a JSON string (e.g., {@code {"X-Gateway":"ibm-bob","X-Tenant":"prod"}}).
     * Returns an empty map if not provided or parsing fails.
     *
     * @return custom headers map
     */
    @WithName("custom-headers")
    @WithConverter(JsonUtils.ConfigPropertyJsonConverter.class)
    Map<String, String> customHeaders();

    /**
     * Sampling temperature (0.0 = deterministic, 1.0 = max randomness).
     *
     * @return the temperature
     */
    @WithName("temperature")
    @WithDefault("0.1")
    double temperature();

    /**
     * Maximum tokens to generate in the response.
     *
     * @return the max tokens
     */
    @WithName("max-tokens")
    @WithDefault("8192")
    int maxTokens();

    /**
     * API key for the LLM provider.
     *
     * @return the API key, or empty if not using API key auth
     */
    @WithName("api-key")
    Optional<String> apiKey();

    /**
     * Request timeout in seconds.
     *
     * @return the timeout in seconds
     */
    @WithName("timeout-seconds")
    @WithDefault("180")
    int timeoutSeconds();

    /**
     * Number of previous messages to retain for conversational follow-ups.
     *
     * @return the chat memory size
     */
    @WithName("chat-memory-size")
    @WithDefault("10")
    int chatMemorySize();

    /**
     * Vertex AI specific configuration.
     *
     * @return the vertex AI config
     */
    @WithName("vertex")
    VertexConfig vertex();

    /**
     * BOB Shell specific configuration.
     *
     * @return the BOB Shell config
     */
    @WithName("bob")
    BobConfig bob();

    /**
     * Vertex AI Configuration
     */
    interface VertexConfig {
        /**
         * Google Cloud project ID.
         *
         * @return the project ID, or empty if not using Vertex AI
         */
        @WithName("project-id")
        Optional<String> projectId();

        /**
         * Google Cloud region (e.g., global, us-east5, europe-west1).
         *
         * @return the location, or empty if not configured
         */
        @WithName("location")
        Optional<String> location();
    }

    /**
     * BOB Shell specific configuration.
     *
     * <p>Only holds parameters that are unique to BOB Shell. Common parameters such as
     * {@code api-key} and {@code timeout-seconds} are read from the top-level
     * {@link LLMConfig} properties so they are not duplicated across providers.
     */
    interface BobConfig {
        /**
         * Path to BOB Shell executable bundled with the application.
         *
         * @return the shell path (default: "bob" assumes it is on the system PATH)
         */
        @WithName("shell-path")
        @WithDefault("bob")
        String shellPath();
    }
}
