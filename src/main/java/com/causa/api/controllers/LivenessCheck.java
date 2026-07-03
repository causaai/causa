package com.causa.api.controllers;

import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness Health Check
 *
 * <p>Indicates whether the application is running. This should only fail if the application
 * is in a broken state that requires a restart (e.g., deadlock, unrecoverable error).
 *
 * <p>Kubernetes uses this to determine if a pod should be restarted.
 *
 * @since 1.0.0
 */
@Liveness
public class LivenessCheck implements HealthCheck {

    private static final CausaLogger log = CausaLogger.getLogger(LivenessCheck.class);

    @Override
    public HealthCheckResponse call() {
        log.debug(LogMessages.Health.LIVENESS_CHECK_CALLED)
            .field(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.UP)
            .log();

        return HealthCheckResponse.named(ApiConstants.Health.LIVENESS_NAME)
                .up()
                .withData(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.UP)
                .withData(ApiConstants.Response.MESSAGE_KEY, ApiConstants.Health.LIVENESS_UP_MESSAGE)
                .build();
    }
}
