package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
import com.causa.config.LLMConfig;
import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
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

    private final AppConfig appConfig;

    @Inject
    public ChatModelFactory(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Produces the ChatModel bean based on the configured provider.
     *
     * <p>No scope annotation on {@code @Produces} — defaults to dependent scope,
     * meaning CDI builds a fresh instance each time it is injected. This ensures
     * the factory always reads the current live {@link AppConfig} values rather
     * than capturing config that may not have been loaded yet at first proxy touch.
     *
     * @return the chat model
     * @throws LLMException if the provider is unsupported or configuration is missing
     */
    @Produces
    public ChatModel chatModel() {
        LLMConfig config = appConfig.getLlmConfig();
        String provider = config.getProvider().orElse(null);

        if (provider == null || provider.isBlank()) {
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

        return switch (provider.toLowerCase()) {
            case LLMConstants.Provider.ANTHROPIC -> buildAnthropicModel();
            case LLMConstants.Provider.VERTEX_AI_ANTHROPIC -> buildVertexAiAnthropicModel();
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
     * Builds an AnthropicChatModel for direct Anthropic API access.
     *
     * @return the Anthropic chat model
     * @throws LLMException if API key is missing
     */
    private ChatModel buildAnthropicModel() {
        LLMConfig config = appConfig.getLlmConfig();
        String apiKey = config.getApiKey().filter(k -> !k.isBlank()).orElse(null);
        if (apiKey == null) {
            log.warn(LogMessages.LLM.MISSING_CONFIGURATION)
                .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.ANTHROPIC)
                .field(LLMConstants.ConfigKeys.MISSING_CONFIG, LLMConstants.ConfigKeys.LLM_API_KEY)
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.API_KEY_REQUIRED + LLMConstants.Provider.ANTHROPIC,
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION
            );
        }

        String modelName = config.getModelName().orElse("");
        log.info(LogMessages.LLM.LLM_PROVIDER_DETECTED)
            .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.ANTHROPIC)
            .field(LLMConstants.Fields.AUTH_TYPE, LLMConstants.AuthModes.API_KEY)
            .field(LLMConstants.Fields.MODEL, modelName)
            .field(LLMConstants.Fields.TEMPERATURE, config.getTemperature())
            .field(LLMConstants.Fields.MAX_TOKENS, config.getMaxTokens())
            .field(LLMConstants.Fields.CACHE_ENABLED, true)
            .log();

        return AnthropicChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
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
     * @return the Vertex AI Anthropic chat model
     * @throws LLMException if project ID or ADC credentials are missing or invalid
     */
    private ChatModel buildVertexAiAnthropicModel() {
        LLMConfig config = appConfig.getLlmConfig();

        String projectId = config.getVertexProjectId().filter(p -> !p.isBlank()).orElse(null);
        if (projectId == null) {
            log.warn(LogMessages.LLM.MISSING_CONFIGURATION)
                .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.VERTEX_AI_ANTHROPIC)
                .field(LLMConstants.ConfigKeys.MISSING_CONFIG, LLMConstants.ConfigKeys.VERTEX_PROJECT_ID)
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.VERTEX_PROJECT_ID_REQUIRED + LLMConstants.Provider.VERTEX_AI_ANTHROPIC,
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION
            );
        }

        String adcBase64 = config.getGoogleApplicationCredentials().filter(s -> !s.isBlank()).orElse(null);
        if (adcBase64 == null) {
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
            byte[] jsonBytes = Base64.getDecoder().decode(adcBase64);
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(jsonBytes));
        } catch (IllegalArgumentException e) {
            throw new LLMException(
                "GOOGLE_APPLICATION_CREDENTIALS is not valid Base64",
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION, e
            );
        } catch (IOException e) {
            throw new LLMException(
                "Failed to parse GOOGLE_APPLICATION_CREDENTIALS as ADC JSON: " + e.getMessage(),
                LLMConstants.ErrorTypes.MISSING_CONFIGURATION, e
            );
        }

        String location = config.getVertexLocation().orElse("us-east5");
        String modelName = config.getModelName().orElse("");

        log.info(LogMessages.LLM.LLM_PROVIDER_DETECTED)
            .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.VERTEX_AI_ANTHROPIC)
            .field(LLMConstants.Fields.AUTH_TYPE, "ADC_JSON")
            .field(LLMConstants.Fields.MODEL, modelName)
            .field(LLMConstants.Fields.VERTEX_PROJECT_ID, projectId)
            .field(LLMConstants.Fields.VERTEX_LOCATION, location)
            .field(LLMConstants.Fields.TEMPERATURE, config.getTemperature())
            .field(LLMConstants.Fields.MAX_TOKENS, config.getMaxTokens())
            .log();

        return VertexAiAnthropicChatModel.builder()
            .project(projectId)
            .location(location)
            .modelName(modelName)
            .maxTokens(config.getMaxTokens())
            .credentials(credentials)
            .logRequests(true)
            .logResponses(true)
            .build();
    }
}
