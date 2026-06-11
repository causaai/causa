package com.causa.common.exceptions;

import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Diagnostic Exception Mapper
 *
 * <p>Maps {@link DiagnosticException} to structured HTTP error responses.
 * <p>Handles diagnostic-specific errors with appropriate logging and status codes.
 *
 * @since 0.0.1
 */
public class DiagnosticExceptionMapper {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticExceptionMapper.class);

    /**
     * Maps DiagnosticException to HTTP 500 response with structured error details.
     *
     * @param exception the diagnostic exception
     * @return HTTP 500 response with error details
     */
    @ServerExceptionMapper
    public Response handleDiagnosticException(DiagnosticException exception) {
        log.error(LogMessages.Diagnostic.DIAGNOSTIC_FAILED)
            .field("errorType", exception.getErrorType())
            .field("message", exception.getMessage())
            .exception(exception)
            .log();

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of(500, "Diagnostic Processing Error", exception.getMessage()))
            .build();
    }
}
