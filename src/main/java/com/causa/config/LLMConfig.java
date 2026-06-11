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
 *
 * @since 0.0.1
 */
@ConfigMapping(prefix = "causa.llm")
public interface LLMConfig {

    /**
     * LLM provider to use (anthropic, vertex-ai-anthropic, openai, ollama).
     *
     * @return the provider name
     */
    @WithName("provider")
    String provider();

    /**
     * Model name to use for LLM requests.
     *
     * @return the model name
     */
    @WithName("model-name")
    String modelName();

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
    double temperature();

    /**
     * Maximum tokens to generate in the response.
     *
     * @return the max tokens
     */
    @WithName("max-tokens")
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
    int timeoutSeconds();

    /**
     * Number of previous messages to retain for conversational follow-ups.
     *
     * @return the chat memory size
     */
    @WithName("chat-memory-size")
    int chatMemorySize();

    /**
     * Vertex AI specific configuration.
     *
     * @return the vertex AI config
     */
    @WithName("vertex")
    VertexConfig vertex();

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
         * @return the location
         */
        @WithName("location")
        String location();
    }
}
