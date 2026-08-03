package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.config.AppConfig;
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
 * current {@code LLM_PROVIDER} value from {@link AppConfig}. This enables runtime
 * switching between LLM providers without restarting the application.
 *
 * <p>Supported providers:
 * <ul>
 *   <li>{@code bob} - Routes to {@link BobShellPromptSender}</li>
 *   <li>All others - Routes to {@link LangChainPromptSender} (anthropic, vertex-ai-anthropic, etc.)</li>
 * </ul>
 *
 * <p>This is the sole CDI {@link PromptSender} bean. Both sender implementations
 * are plain classes instantiated once and held as fields.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class UnifiedPromptSender implements PromptSender {

    private final AppConfig appConfig;
    private final LangChainPromptSender langChainSender;
    private final BobShellPromptSender bobShellSender;

    @Inject
    public UnifiedPromptSender(AppConfig appConfig, ChatModelFactory chatModelFactory, Skills skills) {
        this.appConfig = appConfig;
        this.langChainSender = new LangChainPromptSender(chatModelFactory, appConfig, skills);
        this.bobShellSender = new BobShellPromptSender(appConfig);
    }

    private PromptSender currentSender() {
        String provider = appConfig.getLlmConfig().getProvider();
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
