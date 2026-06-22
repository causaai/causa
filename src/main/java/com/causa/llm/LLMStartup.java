package com.causa.llm;

import com.causa.common.constants.AppConstants;
import com.causa.common.constants.LLMConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * LLM Startup Handler
 *
 * <p>Observes application startup to verify LLM connectivity and log
 * provider configuration. Non-fatal on failure — the application starts but the
 * health check reports DOWN.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LLMStartup {

    private static final CausaLogger log = CausaLogger.getLogger(LLMStartup.class);

    private final PromptSender promptSender;
    private final LLMConfig config;

    @Inject
    public LLMStartup(PromptSender promptSender, LLMConfig config) {
        this.promptSender = promptSender;
        this.config = config;
    }

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.LLM_PRIORITY) StartupEvent event) {
        log.info(LogMessages.LLM.CONNECTIVITY_CHECK_START)
            .field(LLMConstants.Fields.PROVIDER, config.provider())
            .field(LLMConstants.Fields.MODEL, config.modelName())
            .log();

        boolean ready = verifyConnectivity();

        if (ready) {
            log.info(LogMessages.LLM.LLM_READY)
                .field(LLMConstants.Fields.PROVIDER, config.provider())
                .field(LLMConstants.Fields.MODEL, config.modelName())
                .field(LLMConstants.Fields.AUTH_TYPE, config.authType().orElse("NOT_SET"))
                .field(LLMConstants.Fields.TEMPERATURE, config.temperature())
                .field(LLMConstants.Fields.MAX_TOKENS, config.maxTokens())
                .field(LLMConstants.Fields.TIMEOUT_SECONDS, config.timeoutSeconds())
                .log();
        } else {
            log.error(LogMessages.LLM.LLM_STARTUP_FAILED)
                .field(LLMConstants.Fields.PROVIDER, config.provider())
                .log();
        }
    }

    /**
     * Verifies LLM connectivity by sending a minimal test request.
     *
     * @return true if the LLM responded successfully, false otherwise
     */
    private boolean verifyConnectivity() {
        try {
            // Check if provider is ready first
            if (!promptSender.isReady()) {
                log.warn("LLM provider not ready during startup check")
                    .field(LLMConstants.Fields.PROVIDER, config.provider())
                    .log();
                return false;
            }

            LLMRequest testRequest = LLMRequest.builder(LLMConstants.TestData.CONNECTIVITY_TEST_PROMPT)
                .maxTokens(LLMConstants.TestData.CONNECTIVITY_TEST_MAX_TOKENS)
                .build();

            LLMResponse response = promptSender.send(testRequest);

            log.info(LogMessages.LLM.CONNECTIVITY_CHECK_SUCCESS)
                .field(LLMConstants.Fields.PROVIDER, config.provider())
                .field(LLMConstants.Fields.MODEL, response.modelUsed())
                .field(LLMConstants.Fields.LATENCY_MS, response.latencyMs())
                .log();

            return true;

        } catch (Exception e) {
            log.error(LogMessages.LLM.CONNECTIVITY_CHECK_FAILED)
                .field(LLMConstants.Fields.PROVIDER, config.provider())
                .field(LLMConstants.Fields.ERROR_TYPE, e.getClass().getSimpleName())
                .exception(e)
                .log();

            return false;
        }
    }
}
