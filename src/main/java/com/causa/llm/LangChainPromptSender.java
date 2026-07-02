package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LangChain Prompt Sender
 *
 * <p>Implementation of {@link PromptSender} using LangChain4J's {@link ChatLanguageModel}.
 * This adapter wraps the provider-agnostic LangChain4J interface, providing a clean
 * separation between business logic and LLM integration.
 *
 * <p>TEMPORARY: Conditional annotation removed for testing - RESTORE BEFORE PR
 * <p>This bean is enabled by default unless {@code causa.llm.provider} is set to "bob".
 * When "bob" is configured, {@link BobShellPromptSender} is used instead.
 *
 * @since 0.0.1
 */
@ApplicationScoped
@UnlessBuildProperty(name = "causa.llm.provider", stringValue = "bob")
public class LangChainPromptSender implements PromptSender {

    private static final CausaLogger log = CausaLogger.getLogger(LangChainPromptSender.class);

    private final ChatModel chatModel;
    private final LLMConfig config;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Inject
    public LangChainPromptSender(ChatModel chatModel, LLMConfig config) {
        this.chatModel = chatModel;
        this.config = config;
    }

    @Override
    public LLMResponse send(LLMRequest request) {
        if (!isReady()) {
            log.error(LogMessages.LLM.MODEL_NOT_AVAILABLE)
                .field(LLMConstants.Fields.PROVIDER, config.provider())
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.MODEL_NOT_AVAILABLE,
                LLMConstants.ErrorTypes.MODEL_NOT_READY
            );
        }

        log.info(LogMessages.LLM.PROMPT_SEND_START)
            .field(LLMConstants.Fields.PROVIDER, config.provider())
            .field(LLMConstants.Fields.MODEL, resolveModel(request))
            .log();

        long startNanos = System.nanoTime();

        try {
            // Build chat message list
            List<ChatMessage> messages = buildMessages(request);

            // Build chat request with per-request parameter overrides
            ChatRequest chatRequest = buildChatRequest(messages, request);

            // Call the LLM with the configured request
            ChatResponse response = chatModel.chat(chatRequest);

            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            // Extract response data
            String responseText = response.aiMessage().text();

            // Extract token usage
            long inputTokens = response.metadata() != null && response.metadata().tokenUsage() != null
                ? response.metadata().tokenUsage().inputTokenCount() : 0;
            long outputTokens = response.metadata() != null && response.metadata().tokenUsage() != null
                ? response.metadata().tokenUsage().outputTokenCount() : 0;

            // Extract cache tokens (Anthropic-specific) - default to 0 for non-Anthropic providers
            // Note: Cache token tracking is only available in AnthropicChatModel's internal usage object
            // For now, we default to 0. Future enhancement: access via response metadata if available.
            long cacheCreationTokens = 0;
            long cacheReadTokens = 0;

            LLMResponse llmResponse = new LLMResponse(
                responseText,
                resolveModel(request),
                inputTokens,
                outputTokens,
                cacheCreationTokens,
                cacheReadTokens,
                latencyMs
            );

            log.info(LogMessages.LLM.PROMPT_SEND_SUCCESS)
                .field(LLMConstants.Fields.MODEL, llmResponse.modelUsed())
                .field(LLMConstants.Fields.INPUT_TOKENS, llmResponse.inputTokens())
                .field(LLMConstants.Fields.OUTPUT_TOKENS, llmResponse.outputTokens())
                .field(LLMConstants.Fields.CACHE_HIT, llmResponse.wasCacheHit())
                .field(LLMConstants.Fields.CACHE_READ_TOKENS, llmResponse.cacheReadTokens())
                .field(LLMConstants.Fields.CACHE_CREATION_TOKENS, llmResponse.cacheCreationTokens())
                .field(LLMConstants.Fields.LATENCY_MS, llmResponse.latencyMs())
                .log();

            return llmResponse;

        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error(LogMessages.LLM.LLM_ERROR)
                .field(LLMConstants.Fields.ERROR_TYPE, e.getClass().getSimpleName())
                .field(LLMConstants.Fields.LATENCY_MS, latencyMs)
                .exception(e)
                .log();
            throw new LLMException(
                String.format(LLMConstants.ErrorMessages.REQUEST_FAILED_TEMPLATE, e.getMessage()),
                LLMConstants.ErrorTypes.LLM_REQUEST_FAILED,
                e
            );
        }
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    /**
     * Marks the prompt sender as ready. Called by LLMStartup after successful connectivity check.
     */
    void setReady(boolean ready) {
        this.ready.set(ready);
    }

    /**
     * Builds the chat message list from an LLMRequest.
     *
     * @param request the LLM request
     * @return the list of chat messages
     */
    private List<ChatMessage> buildMessages(LLMRequest request) {
        List<ChatMessage> messages = new ArrayList<>();

        // System message (if present)
        if (request.systemPrompt().isPresent() || request.context().isPresent()) {
            String systemText = buildSystemText(request);
            messages.add(SystemMessage.from(systemText));
        }

        // User message
        messages.add(UserMessage.from(request.prompt()));

        return messages;
    }

    /**
     * Builds the system message text from system prompt and context.
     *
     * @param request the LLM request
     * @return the combined system text
     */
    private String buildSystemText(LLMRequest request) {
        StringBuilder sb = new StringBuilder();
        request.systemPrompt().ifPresent(sb::append);
        request.context().ifPresent(ctx -> {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(ctx);
        });
        return sb.toString();
    }

    /**
     * Builds a ChatRequest with per-request parameter overrides.
     *
     * <p>Applies optional parameters from {@link LLMRequest} (maxTokens, temperature).
     * If not specified, the underlying model's configured defaults are used.
     *
     * <p><b>Note on enableCaching:</b> Prompt caching is provider-specific and typically
     * configured at the model level (e.g., Anthropic's prompt caching). The enableCaching
     * flag in LLMRequest is informational and logged for observability, but does not
     * directly control ChatRequestParameters as LangChain4J handles caching at the
     * provider layer.
     *
     * @param messages the chat messages
     * @param request the LLM request containing optional parameter overrides
     * @return a configured ChatRequest
     */
    private ChatRequest buildChatRequest(List<ChatMessage> messages, LLMRequest request) {
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .maxOutputTokens(request.maxTokens().orElse(null))
                .temperature(request.temperature().orElse(null))
                .build();

        return ChatRequest.builder()
                .messages(messages)
                .parameters(parameters)
                .build();
    }

    /**
     * Resolves the effective model name (request override or config default).
     *
     * @param request the LLM request
     * @return the model name
     */
    private String resolveModel(LLMRequest request) {
        return request.modelOverride().orElse(config.modelName());
    }
}
