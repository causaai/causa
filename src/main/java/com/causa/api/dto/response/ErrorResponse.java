package com.causa.api.dto.response;

import java.time.Instant;

/**
 * Error Response DTO
 *
 * <p>Structured error response for API endpoints.
 *
 * @param status HTTP status code
 * @param error error type/category
 * @param message detailed error message
 * @param timestamp error timestamp
 * @since 0.0.1
 */
public record ErrorResponse(
    int status,
    String error,
    String message,
    Instant timestamp
) {

    /**
     * Factory method to create an ErrorResponse.
     *
     * @param status HTTP status code
     * @param error error type/category
     * @param message detailed error message
     * @return the constructed ErrorResponse
     */
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now());
    }
}
