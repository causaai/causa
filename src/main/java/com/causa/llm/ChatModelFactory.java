package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.LlmConfig;
import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

/**
 * Chat Model Factory
 *
 * <p>Produces a {@link ChatLanguageModel} instance based on the configured provider.
 * This is the extensibility point for adding new LLM providers — just add a case
 * in the switch and the corresponding dependency in pom.xml.
 *
 * <p>The model is a CDI singleton (application-scoped), so prompt caching works
 * across requests within the cache TTL window.
 *
 * <p><strong>Supported Providers:</strong>
 * <ul>
 *   <li>{@code anthropic} - Claude via direct Anthropic API (requires LLM_API_KEY)</li>
 *   <li>{@code vertex-ai-anthropic} - Claude via Google Cloud Vertex AI (requires VERTEX_PROJECT_ID
 *       and GOOGLE_APPLICATION_CREDENTIALS as Base64-encoded ADC JSON)</li>
 *   <li>{@code openai} - OpenAI (planned)</li>
 *   <li>{@code ollama} - Ollama local models (planned)</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ChatModelFactory {

    private static final CausaLogger log = CausaLogger.getLogger(ChatModelFactory.class);

    private final LlmConfigCache llmConfigCache;

    @Inject
    public ChatModelFactory(LlmConfigCache llmConfigCache) {
        this.llmConfigCache = llmConfigCache;
    }

    /**
     * Builds a ChatModel instance based on the configured provider.
     *
     * @return the chat model
     * @throws LLMException if the provider is unsupported or configuration is missing
     */
    public ChatModel chatModel() {
        LlmConfig active = llmConfigCache.getActive().orElseThrow(() ->
            new LLMException(
                LLMConstants.ErrorMessages.LLM_CONFIG_NOT_AVAILABLE,
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION
            )
        );
        String provider = active.getProvider().name().toLowerCase();

        if (provider.isBlank()) {
            log.warn(LogMessages.LLM.MISSING_CONFIGURATION)
                .field(LLMConstants.ConfigKeys.MISSING_CONFIG, "LLM_PROVIDER")
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.LLM_CONFIG_NOT_AVAILABLE,
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION
            );
        }

        log.info(LogMessages.LLM.LLM_FACTORY_INITIALIZING)
            .field(LLMConstants.Fields.PROVIDER, provider)
            .log();

        return switch (provider) {
            case LLMConstants.Provider.ANTHROPIC -> buildAnthropicModel(active);
            case "vertex_ai" -> buildVertexAiAnthropicModel(active);
            // Future providers:
            // case LLMConstants.Provider.IBM_BOB -> buildIbmBobModel();  // OpenAI-compatible interface
            // case LLMConstants.Provider.OLLAMA -> buildOllamaModel();
            default -> {
                log.error(LogMessages.LLM.UNSUPPORTED_PROVIDER)
                    .field(LLMConstants.Fields.PROVIDER, provider)
                    .log();
                throw new LLMException(
                    String.format(LLMConstants.ErrorMessages.UNSUPPORTED_PROVIDER_TEMPLATE, provider),
                    LLMConstants.ErrorTypes.UNSUPPORTED_PROVIDER
                );
            }
        };
    }

    /**
     * Returns whether the LLM factory is ready (has valid provider and model configured).
     *
     * @return true if provider and model name are configured
     */
    public boolean isReady() {
        return llmConfigCache.getActive()
            .filter(a -> a.getModels() != null && !a.getModels().isEmpty())
            .isPresent();
    }

    /**
     * Builds an AnthropicChatModel for direct Anthropic API access.
     *
     * @param llm the LLM config snapshot
     * @return the Anthropic chat model
     * @throws LLMException if API key is missing
     */
    private ChatModel buildAnthropicModel(LlmConfig llm) {
        String apiKey = llm.getAuthConfig() != null ? llm.getAuthConfig().apiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn(LogMessages.LLM.MISSING_CONFIGURATION)
                .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.ANTHROPIC)
                .field(LLMConstants.ConfigKeys.MISSING_CONFIG, LLMConstants.ConfigKeys.LLM_API_KEY)
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.API_KEY_REQUIRED + LLMConstants.Provider.ANTHROPIC,
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION
            );
        }

        String modelName = llm.getModels() != null && !llm.getModels().isEmpty() ? llm.getModels().get(0) : "";
        double temperature = llm.getTemperature() != null ? llm.getTemperature().doubleValue() : 0.1;
        int maxTokens = llm.getMaxTokens() != null ? llm.getMaxTokens() : 8192;
        long timeoutSeconds = llm.getTimeoutMs() != null ? llm.getTimeoutMs() / 1000L : 180L;

        log.info(LogMessages.LLM.LLM_PROVIDER_DETECTED)
            .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.ANTHROPIC)
            .field(LLMConstants.Fields.AUTH_TYPE, LLMConstants.AuthModes.API_KEY)
            .field(LLMConstants.Fields.MODEL, modelName)
            .field(LLMConstants.Fields.TEMPERATURE, temperature)
            .field(LLMConstants.Fields.MAX_TOKENS, maxTokens)
            .field(LLMConstants.Fields.CACHE_ENABLED, true)
            .log();

        return AnthropicChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .cacheSystemMessages(true)  // Enable prompt caching for system messages
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    /**
     * Builds a VertexAiAnthropicChatModel for Google Cloud Vertex AI access.
     *
     * <p>Authentication uses the {@code GOOGLE_APPLICATION_CREDENTIALS} config value,
     * which must be a Base64-encoded ADC JSON (personal ADC from
     * {@code gcloud auth application-default login}, or a service account key).
     * The JSON is decoded in-memory and passed directly to {@link GoogleCredentials#fromStream},
     * so no file mount or environment variable is required on the pod.
     *
     * @param llm the LLM config snapshot
     * @return the Vertex AI Anthropic chat model
     * @throws LLMException if project ID or credentials are missing/invalid
     */
    private ChatModel buildVertexAiAnthropicModel(LlmConfig llm) {
        String projectId = additionalConfig(llm, "projectId");
        if (projectId == null || projectId.isBlank()) {
            log.warn(LogMessages.LLM.MISSING_CONFIGURATION)
                .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.VERTEX_AI_ANTHROPIC)
                .field(LLMConstants.ConfigKeys.MISSING_CONFIG, LLMConstants.ConfigKeys.VERTEX_PROJECT_ID)
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.VERTEX_PROJECT_ID_REQUIRED + LLMConstants.Provider.VERTEX_AI_ANTHROPIC,
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION
            );
        }

        String adcBase64 = llm.getAuthConfig() != null ? llm.getAuthConfig().credentialsJson() : null;
        if (adcBase64 == null || adcBase64.isBlank()) {
            log.warn(LogMessages.LLM.MISSING_CONFIGURATION)
                .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.VERTEX_AI_ANTHROPIC)
                .field(LLMConstants.ConfigKeys.MISSING_CONFIG, "GOOGLE_APPLICATION_CREDENTIALS")
                .log();
            throw new LLMException(
                "GOOGLE_APPLICATION_CREDENTIALS (Base64 ADC JSON) is required for provider: "
                    + LLMConstants.Provider.VERTEX_AI_ANTHROPIC,
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION
            );
        }

        GoogleCredentials credentials;
        try {
            byte[] jsonBytes = Base64.getDecoder().decode(adcBase64.replaceAll("\\s", ""));
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(jsonBytes));
        } catch (IllegalArgumentException e) {
            throw new LLMException(
                "GOOGLE_APPLICATION_CREDENTIALS is not valid Base64",
                LLMConstants.ErrorTypes.INVALID_CONFIGURATION, e
            );
        } catch (IOException e) {
            throw new LLMException(
                "Failed to parse GOOGLE_APPLICATION_CREDENTIALS as ADC JSON: " + e.getMessage(),
                LLMConstants.ErrorTypes.INVALID_CONFIGURATION, e
            );
        }

        String location = additionalConfig(llm, "location");
        if (location == null || location.isBlank()) {
            location = "us-east5";
        }
        String modelName = llm.getModels() != null && !llm.getModels().isEmpty() ? llm.getModels().get(0) : "";
        int maxTokens = llm.getMaxTokens() != null ? llm.getMaxTokens() : 8192;

        log.info(LogMessages.LLM.LLM_PROVIDER_DETECTED)
            .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.VERTEX_AI_ANTHROPIC)
            .field(LLMConstants.Fields.AUTH_TYPE, LLMConstants.AuthModes.ADC_JSON)
            .field(LLMConstants.Fields.MODEL, modelName)
            .field(LLMConstants.Fields.VERTEX_PROJECT_ID, projectId)
            .field(LLMConstants.Fields.VERTEX_LOCATION, location)
            .field(LLMConstants.Fields.TEMPERATURE, llm.getTemperature())
            .field(LLMConstants.Fields.MAX_TOKENS, maxTokens)
            .log();

        return VertexAiAnthropicChatModel.builder()
            .project(projectId)
            .location(location)
            .modelName(modelName)
            .maxTokens(maxTokens)
            .credentials(credentials)
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    /** Reads a string value from {@code additionalConfig}, returning null if absent. */
    private static String additionalConfig(LlmConfig llm, String key) {
        if (llm.getAdditionalConfig() == null) return null;
        Object val = llm.getAdditionalConfig().get(key);
        return val != null ? val.toString() : null;
    }
}
