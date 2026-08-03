package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.skills.Skills;

import java.util.ArrayList;
import java.util.List;

/**
 * LangChain Prompt Sender
 *
 * <p>Implementation of {@link PromptSender} using LangChain4J's {@link ChatModel}.
 * This adapter wraps the provider-agnostic LangChain4J interface, providing a clean
 * separation between business logic and LLM integration.
 *
 * <p>This is a plain class (not a CDI bean) instantiated by {@link UnifiedPromptSender}.
 * It builds a fresh {@link ChatModel} from current config on each request via {@link ChatModelFactory}.
 *
 * @since 0.0.1
 */
public class LangChainPromptSender implements PromptSender {

    private static final CausaLogger log = CausaLogger.getLogger(LangChainPromptSender.class);

    private final ChatModelFactory chatModelFactory;
    private final AppConfig appConfig;
    private final Skills skills;

    public LangChainPromptSender(ChatModelFactory chatModelFactory, AppConfig appConfig, Skills skills) {
        this.chatModelFactory = chatModelFactory;
        this.appConfig = appConfig;
        this.skills = skills;
    }

    @Override
    public LLMResponse send(LLMRequest request) {
        if (!isReady()) {
            String provider = appConfig.getLlmConfig().getProvider();
            log.error(LogMessages.LLM.MODEL_NOT_AVAILABLE)
                .field(LLMConstants.Fields.PROVIDER, provider != null && !provider.isBlank() ? provider : "(not configured)")
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.MODEL_NOT_AVAILABLE,
                LLMConstants.ErrorTypes.MODEL_NOT_READY
            );
        }

        String provider = appConfig.getLlmConfig().getProvider();
        log.info(LogMessages.LLM.PROMPT_SEND_START)
            .field(LLMConstants.Fields.PROVIDER, provider != null && !provider.isBlank() ? provider : "(not configured)")
            .field(LLMConstants.Fields.MODEL, resolveModel(request))
            .log();

        long startNanos = System.nanoTime();

        try {
            // Build chat message list
            List<ChatMessage> messages = buildMessages(request);

            // Execute LLM call with tool execution loop
            ChatResponse response = executeWithToolLoop(messages, request);

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
        String modelName = appConfig.getLlmConfig().getModelName();
        return chatModelFactory != null && modelName != null && !modelName.isBlank();
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
        String systemText = buildSystemText(request);
        if (!systemText.isBlank()) {
            messages.add(SystemMessage.from(systemText));
        }

        // User message
        messages.add(UserMessage.from(request.prompt()));

        return messages;
    }

    /**
     * Builds the system message text from system prompt, context, and skills catalogue.
     *
     * <p>Implements preemptive skill disclosure by injecting the skills catalogue
     * (name + description only) into the system message. The LLM can then call
     * activate_skill("skill-name") to load full content on-demand.
     *
     * @param request the LLM request
     * @return the combined system text
     */
    private String buildSystemText(LLMRequest request) {
        StringBuilder sb = new StringBuilder();

        // Add skills catalogue (preemptive disclosure) only if enabled
        boolean skillsEnabled = request.enableSkills().orElse(appConfig.getLlmConfig().isSkillsEnabled());
        if (skillsEnabled && skills != null) {
            String catalogue = skills.formatAvailableSkills();
            if (catalogue != null && !catalogue.isBlank()) {
                sb.append("You have access to the following skills:\n");
                sb.append(catalogue);
                sb.append("\n");
            }
        }

        // Add custom system prompt
        request.systemPrompt().ifPresent(prompt -> {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(prompt);
        });

        // Add context
        request.context().ifPresent(ctx -> {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(ctx);
        });

        return sb.toString();
    }

    /**
     * Executes LLM call with automatic tool execution loop.
     *
     * <p>Implements multi-turn tool calling:
     * <ol>
     *   <li>Call LLM with tool specifications (e.g., activate_skill)</li>
     *   <li>If LLM requests tool execution, execute the tool</li>
     *   <li>Add tool result to conversation and call LLM again</li>
     *   <li>Repeat until LLM returns text response (max 5 iterations)</li>
     * </ol>
     *
     * @param messages the conversation messages
     * @param request  the LLM request
     * @return the final chat response
     */
    private ChatResponse executeWithToolLoop(List<ChatMessage> messages, LLMRequest request) {
        final int maxToolIterations = LLMConstants.Defaults.MAX_TOOL_ITERATIONS;

        ChatModel chatModel = chatModelFactory.chatModel();

        boolean skillsEnabled = request.enableSkills().orElse(appConfig.getLlmConfig().isSkillsEnabled());
        boolean hasTools = skillsEnabled && skills != null && skills.toolProvider() != null;

        for (int iteration = 0; iteration < maxToolIterations; iteration++) {
            // Computed once per iteration — shared by buildChatRequest and executor lookup below.
            var toolProviderResult = hasTools
                    ? skills.toolProvider().provideTools(toolProviderRequest(messages))
                    : null;

            ChatRequest chatRequest = buildChatRequest(messages, request, toolProviderResult);
            ChatResponse response = chatModel.chat(chatRequest);

            // No tool calls — return the final text response
            if (!response.aiMessage().hasToolExecutionRequests()) {
                return response;
            }

            log.info("LLM requested tool execution(s)")
                    .field("tool_count", response.aiMessage().toolExecutionRequests().size())
                    .field("iteration", iteration + 1)
                    .log();

            messages.add(response.aiMessage());

            for (ToolExecutionRequest toolRequest : response.aiMessage().toolExecutionRequests()) {
                try {
                    log.info("Executing tool")
                            .field("tool_name", toolRequest.name())
                            .field("arguments", toolRequest.arguments())
                            .log();

                    var toolExecutor = toolProviderResult.toolExecutorByName(toolRequest.name());
                    if (toolExecutor == null) {
                        throw new IllegalArgumentException(
                                String.format(LLMConstants.ErrorMessages.TOOL_NOT_FOUND_TEMPLATE, toolRequest.name()));
                    }
                    // executeWithContext required — execute() throws in AbstractSkillToolExecutor
                    var result = toolExecutor.executeWithContext(toolRequest, null);
                    String toolResult = result.resultText();

                    log.info("Tool execution completed")
                            .field("tool_name", toolRequest.name())
                            .field("result_length", toolResult != null ? toolResult.length() : 0)
                            .log();

                    // attributes carries "activated_skill" — read by Skills in the next iteration
                    messages.add(ToolExecutionResultMessage.builder()
                            .id(toolRequest.id())
                            .toolName(toolRequest.name())
                            .text(toolResult)
                            .attributes(result.attributes())
                            .build());

                } catch (Exception e) {
                    log.error("Tool execution failed")
                            .field("tool_name", toolRequest.name())
                            .field("error_class", e.getClass().getName())
                            .field("error_message", e.getMessage())
                            .field("cause", e.getCause() != null ? e.getCause().getMessage() : "null")
                            .exception(e)
                            .log();

                    messages.add(ToolExecutionResultMessage.from(toolRequest,
                            String.format(LLMConstants.ErrorMessages.TOOL_EXECUTION_FAILED_TEMPLATE,
                                    e.getClass().getSimpleName(), e.getMessage())));
                }
            }
        }

        // Max iterations reached without a text response — runaway tool loop
        throw new LLMException(
                String.format(LLMConstants.ErrorMessages.MAX_TOOL_ITERATIONS_TEMPLATE, maxToolIterations),
                LLMConstants.ErrorTypes.LLM_REQUEST_FAILED
        );
    }

    /**
     * Builds a ChatRequest with per-request parameter overrides and tool specifications.
     *
     * <p>Applies optional parameters from {@link LLMRequest} (maxTokens, temperature).
     * If not specified, the underlying model's configured defaults are used.
     *
     * <p>Registers tool specifications from the supplied {@code toolProviderResult} so the
     * LLM can call tools like {@code activate_skill} and {@code read_skill_resource}.
     * The caller is responsible for computing the result once per iteration and passing it
     * here, avoiding a redundant {@code provideTools()} invocation.
     *
     * <p><b>Note on enableCaching:</b> Prompt caching is provider-specific and typically
     * configured at the model level (e.g., Anthropic's prompt caching). The enableCaching
     * flag in LLMRequest is informational and logged for observability, but does not
     * directly control ChatRequestParameters as LangChain4J handles caching at the
     * provider layer.
     *
     * @param messages           the chat messages
     * @param request            the LLM request containing optional parameter overrides
     * @param toolProviderResult pre-computed tool provider result for this iteration,
     *                           or {@code null} when skills are disabled
     * @return a configured ChatRequest
     */
    private ChatRequest buildChatRequest(List<ChatMessage> messages, LLMRequest request,
            dev.langchain4j.service.tool.ToolProviderResult toolProviderResult) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(messages);

        if (toolProviderResult != null) {
            builder.toolSpecifications(
                    toolProviderResult.tools().keySet().stream()
                            .toArray(dev.langchain4j.agent.tool.ToolSpecification[]::new)
            );
            if (request.maxTokens().isPresent()) {
                builder.maxOutputTokens(request.maxTokens().get());
            }
            if (request.temperature().isPresent()) {
                builder.temperature(request.temperature().get());
            }
        } else {
            ChatRequestParameters parameters = ChatRequestParameters.builder()
                    .maxOutputTokens(request.maxTokens().orElse(null))
                    .temperature(request.temperature().orElse(null))
                    .build();
            builder.parameters(parameters);
        }

        return builder.build();
    }

    /**
     * Wraps the message history in a {@link ToolProviderRequest} for the Skills tool provider.
     * Supplies the minimum fields required by the builder ({@code userMessage} and a stub
     * {@code InvocationContext}) since we call {@link ChatModel} directly, not via an AI service.
     */
    private ToolProviderRequest toolProviderRequest(List<ChatMessage> messages) {
        UserMessage userMsg = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> (UserMessage) m)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException(LLMConstants.ErrorMessages.NO_USER_MESSAGE));
        return ToolProviderRequest.builder()
                .userMessage(userMsg)
                .messages(messages)
                .invocationContext(
                        dev.langchain4j.invocation.InvocationContext.builder()
                                .chatMemoryId(null)
                                .build()
                )
                .build();
    }

    /**
     * Resolves the effective model name (request override or config default).
     *
     * @param request the LLM request
     * @return the model name
     */
    private String resolveModel(LLMRequest request) {
        return request.modelOverride().orElse(appConfig.getLlmConfig().getModelName());
    }
}
