package com.causa.common.exceptions;

import com.causa.api.dto.response.ErrorResponse;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Global Exception Mapper
 *
 * <p>Application-wide exception handler for all exceptions.
 * <p>Handles domain exceptions (Alert, Diagnostic), HTTP exceptions (400, 404, 405), and unexpected errors.
 *
 * @since 0.0.1
 */
public class GlobalExceptionMapper {

    private static final CausaLogger log = CausaLogger.getLogger(GlobalExceptionMapper.class);

    /**
     * Handles AlertException with proper logging and error response.
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

    /**
     * Handles DiagnosticException with proper logging and error response.
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

    /**
     * Handles 404 Not Found exceptions.
     *
     * @param exception the not found exception
     * @return HTTP 404 response with structured error details
     */
    @ServerExceptionMapper
    public Response handleNotFoundException(NotFoundException exception) {
        log.debug("Resource not found")
            .field("path", exception.getMessage())
            .log();

        return Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse.of(404, "Not Found",
                "The requested resource was not found"))
            .build();
    }

    /**
     * Handles 405 Method Not Allowed exceptions.
     *
     * @param exception the method not allowed exception
     * @return HTTP 405 response with structured error details
     */
    @ServerExceptionMapper
    public Response handleMethodNotAllowedException(NotAllowedException exception) {
        log.debug("Method not allowed")
            .field("message", exception.getMessage())
            .log();

        return Response.status(Response.Status.METHOD_NOT_ALLOWED)
            .entity(ErrorResponse.of(405, "Method Not Allowed",
                "The HTTP method is not supported for this endpoint"))
            .build();
    }

    /**
     * Handles invalid pagination/sort/filter parameter exceptions.
     *
     * <p>Produced by the service layer when {@code page}, {@code page_size},
     * {@code sort}, or {@code sort_dir} values fail validation rules.
     *
     * @param exception the invalid pagination exception
     * @return HTTP 400 response with the validation message
     */
    @ServerExceptionMapper
    public Response handleInvalidPaginationException(InvalidPaginationException exception) {
        log.debug(LogMessages.Pagination.INVALID_PARAM)
            .field("message", exception.getMessage())
            .log();

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(400, "Bad Request", exception.getMessage()))
            .build();
    }

    /**
     * Handles 400 Bad Request exceptions.
     *
     * @param exception the bad request exception
     * @return HTTP 400 response with structured error details
     */
    @ServerExceptionMapper
    public Response handleBadRequestException(BadRequestException exception) {
        log.debug("Bad request")
            .field("message", exception.getMessage())
            .log();

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ErrorResponse.of(400, "Bad Request",
                exception.getMessage() != null ? exception.getMessage() : "The request was malformed or invalid"))
            .build();
    }

    /**
     * Handles other WebApplicationException instances (e.g., 401, 403, etc.).
     *
     * @param exception the web application exception
     * @return HTTP response matching the exception status with structured error details
     */
    @ServerExceptionMapper
    public Response handleWebApplicationException(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();

        // Only log as warning for server errors (5xx)
        if (status >= 500) {
            log.warn("Web application exception")
                .field("status", status)
                .field("message", exception.getMessage())
                .exception(exception)
                .log();
        } else {
            log.debug("Web application exception")
                .field("status", status)
                .field("message", exception.getMessage())
                .log();
        }

        String message = exception.getMessage() != null ? exception.getMessage() : "An error occurred";
        String title = Response.Status.fromStatusCode(status) != null
            ? Response.Status.fromStatusCode(status).getReasonPhrase()
            : "Error";

        return Response.status(status)
            .entity(ErrorResponse.of(status, title, message))
            .build();
    }

    /**
     * Handles unexpected exceptions (non-HTTP exceptions).
     *
     * @param exception the caught exception
     * @return HTTP 500 response with structured error details
     */
    @ServerExceptionMapper
    public Response handleException(Exception exception) {
        log.error(LogMessages.UNEXPECTED_ERROR)
            .field("exceptionType", exception.getClass().getSimpleName())
            .field("message", exception.getMessage())
            .exception(exception)
            .log();

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(ErrorResponse.of(500, "Internal Server Error",
                "An unexpected error occurred while processing your request"))
            .build();
    }
}


