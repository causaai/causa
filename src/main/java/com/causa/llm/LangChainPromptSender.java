package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.arc.properties.UnlessBuildProperty;
import dev.langchain4j.skills.Skills;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LangChain Prompt Sender
 *
 * <p>Implementation of {@link PromptSender} using LangChain4J's {@link ChatModel}.
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
    private final Skills skills;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Inject
    public LangChainPromptSender(ChatModel chatModel, LLMConfig config, Skills skills) {
        this.chatModel = chatModel;
        this.config = config;
        this.skills = skills;
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
        boolean skillsEnabled = request.enableSkills().orElse(true);
        if (skillsEnabled && skills != null) {
            String catalogue = skills.formatAvailableSkills();
            if (catalogue != null && !catalogue.isBlank()) {
                sb.append("You have access to the following skills:\n\n");
                sb.append(catalogue);
                sb.append("\n\n# Tool Usage Instructions\n");
                sb.append("When the user's request relates to one of the skills listed above:\n");
                sb.append("1. Use the EXACT skill name from the list above (e.g., 'kubernetes-diagnostics')\n");
                sb.append("2. Call: activate_skill(\"exact-skill-name-from-list\")\n");
                sb.append("3. DO NOT make up skill names or paraphrase them\n");
                sb.append("4. The skill name parameter must match exactly as shown above\n\n");
            }
        }

        // Add custom system prompt
        request.systemPrompt().ifPresent(prompt -> {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(prompt);
        });

        // Add context
        request.context().ifPresent(ctx -> {
            if (sb.length() > 0) {
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
     * @param request the LLM request
     * @return the final chat response
     */
    private ChatResponse executeWithToolLoop(List<ChatMessage> messages, LLMRequest request) {
        final int MAX_TOOL_ITERATIONS = 5;
        int iteration = 0;

        ChatResponse response = null;
        while (iteration < MAX_TOOL_ITERATIONS) {
            // Build chat request with tool specifications
            ChatRequest chatRequest = buildChatRequest(messages, request);

            // Call the LLM
            response = chatModel.chat(chatRequest);

            // Check if LLM wants to execute tools
            if (!response.aiMessage().hasToolExecutionRequests()) {
                // No tool calls - return final response
                return response;
            }

            // Execute requested tools
            log.info("LLM requested tool execution(s)")
                .field("tool_count", response.aiMessage().toolExecutionRequests().size())
                .field("iteration", iteration + 1)
                .log();

            // Add AI message (with tool requests) to conversation
            messages.add(response.aiMessage());

            // Execute each tool and add results
            for (ToolExecutionRequest toolRequest : response.aiMessage().toolExecutionRequests()) {
                try {
                    log.info("Executing tool")
                        .field("tool_name", toolRequest.name())
                        .field("arguments", toolRequest.arguments())
                        .log();

                    // Execute tool via skills.toolProvider()
                    var toolProviderResult = skills.toolProvider().provideTools(null);
                    var toolExecutor = toolProviderResult.aiServiceTools().stream()
                        .filter(tool -> tool.name().equals(toolRequest.name()))
                        .findFirst()
                        .map(tool -> tool.toolExecutor())
                        .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolRequest.name()));
                    var toolExecutionResult = toolExecutor.executeWithContext(toolRequest, null);
                    String toolResult = String.valueOf(toolExecutionResult.result());

                    log.info("Tool execution completed")
                        .field("tool_name", toolRequest.name())
                        .field("result_length", toolResult.length())
                        .log();

                    // Add tool result to conversation
                    messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));

                } catch (Exception e) {
                    log.error("Tool execution failed")
                        .field("tool_name", toolRequest.name())
                        .field("error_class", e.getClass().getName())
                        .field("error_message", e.getMessage())
                        .field("cause", e.getCause() != null ? e.getCause().getMessage() : "null")
                        .exception(e)
                        .log();

                    // Return error to LLM so it can handle gracefully
                    String errorMessage = "Tool execution failed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                    messages.add(ToolExecutionResultMessage.from(toolRequest, errorMessage));
                }
            }

            iteration++;
        }

        // Max iterations reached - return last response from the loop
        log.warn("Max tool execution iterations reached")
            .field("max_iterations", MAX_TOOL_ITERATIONS)
            .log();
        return response;
    }

    /**
     * Builds a ChatRequest with per-request parameter overrides and tool specifications.
     *
     * <p>Applies optional parameters from {@link LLMRequest} (maxTokens, temperature).
     * If not specified, the underlying model's configured defaults are used.
     *
     * <p>Registers tool specifications from {@link Skills#toolProvider()} so the LLM
     * can call tools like {@code activate_skill} and {@code read_skill_resource}.
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
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(messages);

        // Register tool specifications from skills (activate_skill, read_skill_resource, etc.)
        boolean skillsEnabled = request.enableSkills().orElse(true);
        boolean hasTools = skillsEnabled && skills != null && skills.toolProvider() != null;

        if (hasTools) {
            var toolProviderResult = skills.toolProvider().provideTools(null);
            builder.toolSpecifications(
                toolProviderResult.aiServiceTools().stream()
                    .map(tool -> tool.toolSpecification())
                    .toArray(dev.langchain4j.agent.tool.ToolSpecification[]::new)
            );

            // When using tools, set parameters directly on builder
            if (request.maxTokens().isPresent()) {
                builder.maxOutputTokens(request.maxTokens().get());
            }
            if (request.temperature().isPresent()) {
                builder.temperature(request.temperature().get());
            }
        } else {
            // When NOT using tools, use parameters object
            ChatRequestParameters parameters = ChatRequestParameters.builder()
                    .maxOutputTokens(request.maxTokens().orElse(null))
                    .temperature(request.temperature().orElse(null))
                    .build();
            builder.parameters(parameters);
        }

        return builder.build();
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
