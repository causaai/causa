package com.causa.common.exceptions;

import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Alert Exception Handler
 *
 * <p>Global exception handler for alert webhook processing.
 * <p>Catches unhandled exceptions and returns structured error responses.
 *
 * @since 0.0.1
 */
public class AlertExceptionHandler {

    private static final CausaLogger log = CausaLogger.getLogger(AlertExceptionHandler.class);

    /**
     * Handles unhandled exceptions during alert processing.
     *
     * @param exception the caught exception
     * @return HTTP 500 response with structured error details
     */
    @ServerExceptionMapper
    public Response handleException(Exception exception) {
        log.error(LogMessages.Alert.ALERT_PROCESSING_ERROR)
            .field("exceptionType", exception.getClass().getSimpleName())
            .field("message", exception.getMessage())
            .exception(exception)
            .log();

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of(500, "Internal Server Error",
                "An unexpected error occurred processing the alert webhook"))
            .build();
    }
}


