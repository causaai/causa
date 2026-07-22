package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LLMConfig;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.time.Duration;

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
 *   <li>{@code vertex-ai-anthropic} - Claude via Google Cloud Vertex AI (requires VERTEX_PROJECT_ID, uses ADC)</li>
 *   <li>{@code openai} - OpenAI (planned)</li>
 *   <li>{@code ollama} - Ollama local models (planned)</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ChatModelFactory {

    private static final CausaLogger log = CausaLogger.getLogger(ChatModelFactory.class);

    private final LLMConfig config;

    @Inject
    public ChatModelFactory(LLMConfig config) {
        this.config = config;
    }

    /**
     * Produces the ChatModel bean based on the configured provider.
     *
     * @return the chat model
     * @throws LLMException if the provider is unsupported or configuration is missing
     */
    @Produces
    @ApplicationScoped
    public ChatModel chatModel() {
        String provider = config.provider().orElse(null);

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
        String apiKey = config.apiKey().filter(k -> !k.isBlank()).orElse(null);
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

        String modelName = config.modelName().orElse("");
        log.info(LogMessages.LLM.LLM_PROVIDER_DETECTED)
            .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.ANTHROPIC)
            .field(LLMConstants.Fields.AUTH_TYPE, LLMConstants.AuthModes.API_KEY)
            .field(LLMConstants.Fields.MODEL, modelName)
            .field(LLMConstants.Fields.TEMPERATURE, config.temperature())
            .field(LLMConstants.Fields.MAX_TOKENS, config.maxTokens())
            .field(LLMConstants.Fields.CACHE_ENABLED, true)
            .log();

        return AnthropicChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(config.temperature())
            .maxTokens(config.maxTokens())
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .cacheSystemMessages(true)  // Enable prompt caching for system messages
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    /**
     * Builds a VertexAiAnthropicChatModel for Google Cloud Vertex AI access.
     *
     * @return the Vertex AI Anthropic chat model
     * @throws LLMException if project ID is missing
     */
    private ChatModel buildVertexAiAnthropicModel() {
        String projectId = config.vertex().projectId().filter(p -> !p.isBlank()).orElse(null);
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

        String location = config.vertex().location().orElse("us-east5");
        String modelName = config.modelName().orElse("");

        log.info(LogMessages.LLM.LLM_PROVIDER_DETECTED)
            .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.VERTEX_AI_ANTHROPIC)
            .field(LLMConstants.Fields.AUTH_TYPE, LLMConstants.AuthModes.ADC)
            .field(LLMConstants.Fields.MODEL, modelName)
            .field(LLMConstants.Fields.VERTEX_PROJECT_ID, projectId)
            .field(LLMConstants.Fields.VERTEX_LOCATION, location)
            .field(LLMConstants.Fields.TEMPERATURE, config.temperature())
            .field(LLMConstants.Fields.MAX_TOKENS, config.maxTokens())
            .log();

        return VertexAiAnthropicChatModel.builder()
            .project(projectId)
            .location(location)
            .modelName(modelName)
            .maxTokens(config.maxTokens())
            .logRequests(true)
            .logResponses(true)
            .build();
    }
}
