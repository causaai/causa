package com.causa.llm;

import com.causa.common.constants.AppConstants;
import com.causa.common.constants.LlmConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LlmConfig;
import com.causa.core.domain.LlmRequest;
import com.causa.core.domain.LlmResponse;
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
public class LlmStartup {

    private static final CausaLogger log = CausaLogger.getLogger(LlmStartup.class);

    private final LangChainPromptSender promptSender;
    private final LlmConfig config;

    @Inject
    public LlmStartup(LangChainPromptSender promptSender, LlmConfig config) {
        this.promptSender = promptSender;
        this.config = config;
    }

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.LLM_PRIORITY) StartupEvent event) {
        log.info(LogMessages.Llm.CONNECTIVITY_CHECK_START)
            .field(LlmConstants.Fields.PROVIDER, config.provider())
            .field(LlmConstants.Fields.MODEL, config.modelName())
            .log();

        boolean ready = verifyConnectivity();
        promptSender.setReady(ready);

        if (ready) {
            log.info(LogMessages.Llm.LLM_READY)
                .field(LlmConstants.Fields.PROVIDER, config.provider())
                .field(LlmConstants.Fields.MODEL, config.modelName())
                .field(LlmConstants.Fields.AUTH_TYPE, config.authType().orElse("NOT_SET"))
                .field(LlmConstants.Fields.TEMPERATURE, config.temperature())
                .field(LlmConstants.Fields.MAX_TOKENS, config.maxTokens())
                .field(LlmConstants.Fields.TIMEOUT_SECONDS, config.timeoutSeconds())
                .log();
        } else {
            log.error(LogMessages.Llm.LLM_STARTUP_FAILED)
                .field(LlmConstants.Fields.PROVIDER, config.provider())
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
            LlmRequest testRequest = LlmRequest.builder(LlmConstants.TestData.CONNECTIVITY_TEST_PROMPT)
                .maxTokens(LlmConstants.TestData.CONNECTIVITY_TEST_MAX_TOKENS)
                .build();

            // Temporarily set ready to true to allow the test request
            promptSender.setReady(true);

            LlmResponse response = promptSender.send(testRequest);

            log.info(LogMessages.Llm.CONNECTIVITY_CHECK_SUCCESS)
                .field(LlmConstants.Fields.PROVIDER, config.provider())
                .field(LlmConstants.Fields.MODEL, response.modelUsed())
                .field(LlmConstants.Fields.LATENCY_MS, response.latencyMs())
                .log();

            return true;

        } catch (Exception e) {
            log.error(LogMessages.Llm.CONNECTIVITY_CHECK_FAILED)
                .field(LlmConstants.Fields.PROVIDER, config.provider())
                .field(LlmConstants.Fields.ERROR_TYPE, e.getClass().getSimpleName())
                .exception(e)
                .log();

            return false;
        }
    }
}
