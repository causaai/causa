package com.causa.infrastructure.health;

import com.causa.common.constants.ApiConstants;
import com.causa.common.constants.LLMConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.ports.llm.PromptSender;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * LLM Health Check
 *
 * <p>MicroProfile readiness health check for LLM connectivity.
 * Reports UP when the LLM provider is initialized and responsive.
 *
 * <p>Kubernetes uses this to determine if the pod should receive traffic.
 *
 * @since 0.0.1
 */
@Readiness
public class LLMHealthCheck implements HealthCheck {

    private static final CausaLogger log = CausaLogger.getLogger(LLMHealthCheck.class);

    private final PromptSender promptSender;

    @Inject
    public LLMHealthCheck(PromptSender promptSender) {
        this.promptSender = promptSender;
    }

    @Override
    public HealthCheckResponse call() {
        boolean isReady = promptSender.isReady();

        if (isReady) {
            log.debug(LogMessages.Health.LLM_READINESS_PASSED)
                .field(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.READY)
                .log();

            return HealthCheckResponse.named(LLMConstants.Health.LLM_HEALTH_NAME)
                    .up()
                    .withData(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.READY)
                    .withData(ApiConstants.Response.MESSAGE_KEY, LLMConstants.Health.LLM_UP_MESSAGE)
                    .build();
        } else {
            log.warn(LogMessages.Health.LLM_READINESS_FAILED)
                .field(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.NOT_READY)
                .log();

            return HealthCheckResponse.named(LLMConstants.Health.LLM_HEALTH_NAME)
                    .down()
                    .withData(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.NOT_READY)
                    .withData(ApiConstants.Response.MESSAGE_KEY, LLMConstants.Health.LLM_DOWN_MESSAGE)
                    .build();
        }
    }
}
