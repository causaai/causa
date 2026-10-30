package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import dev.langchain4j.skills.Skills;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Unified Prompt Sender
 *
 * <p>Routes LLM requests to the appropriate sender implementation based on the
 * active provider in {@link LlmConfigCache}. This enables runtime switching between
 * LLM providers without restarting the application.
 *
 * <p>Supported providers:
 * <ul>
 *   <li>{@code bob} - Routes to {@link BobShellPromptSender}</li>
 *   <li>All others - Routes to {@link LangChainPromptSender} (anthropic, vertex_ai, etc.)</li>
 * </ul>
 *
 * <p>This is the sole CDI {@link PromptSender} bean. Both sender implementations
 * are plain classes instantiated once and held as fields.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class UnifiedPromptSender implements PromptSender {

    private final LlmConfigCache llmConfigCache;
    private final LangChainPromptSender langChainSender;
    private final BobShellPromptSender bobShellSender;

    @Inject
    public UnifiedPromptSender(LlmConfigCache llmConfigCache, ChatModelFactory chatModelFactory, Skills skills) {
        this.llmConfigCache = llmConfigCache;
        this.langChainSender = new LangChainPromptSender(chatModelFactory, llmConfigCache, skills);
        this.bobShellSender = new BobShellPromptSender(llmConfigCache);
    }

    private PromptSender currentSender() {
        String provider = llmConfigCache.getActive()
            .map(a -> a.getProvider().name().toLowerCase())
            .orElse("");
        if (LLMConstants.Provider.IBM_BOB.equalsIgnoreCase(provider)) {
            return bobShellSender;
        }
        return langChainSender;
    }

    @Override
    public LLMResponse send(LLMRequest request) {
        return currentSender().send(request);
    }

    @Override
    public boolean isReady() {
        return currentSender().isReady();
    }
}
