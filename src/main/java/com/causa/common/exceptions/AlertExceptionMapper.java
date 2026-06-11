package com.causa.common.exceptions;

import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Alert Exception Mapper
 *
 * <p>Maps {@link AlertException} to structured HTTP error responses.
 * <p>Handles alert-specific errors with appropriate logging and status codes.
 *
 * @since 0.0.1
 */
public class AlertExceptionMapper {

    private static final CausaLogger log = CausaLogger.getLogger(AlertExceptionMapper.class);

    /**
     * Maps AlertException to HTTP 500 response with structured error details.
     *
     * @param exception the alert exception
     * @return HTTP 500 response with error details
     */
    @ServerExceptionMapper
    public Response handleAlertException(AlertException exception) {
        log.error(LogMessages.Alert.ALERT_PROCESSING_ERROR)
            .field("errorType", exception.getErrorType())
            .field("message", exception.getMessage())
            .exception(exception)
            .log();

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of(500, "Alert Processing Error", exception.getMessage()))
            .build();
    }
}
