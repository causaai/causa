package com.causa.common.exceptions;

/**
 * Thrown when a pagination, sorting, or filter query parameter fails validation.
 *
 * <p>Mapped to HTTP 400 Bad Request by
 * {@link GlobalExceptionMapper#handleInvalidPaginationException}.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code page=0} — page must be ≥ 1</li>
 *   <li>{@code page_size=200} — page_size exceeds the maximum of 100</li>
 *   <li>{@code sort=password} — sort field not in the whitelist</li>
 *   <li>{@code sort_dir=random} — sort direction must be {@code asc} or {@code desc}</li>
 * </ul>
 *
 * @since 0.0.2
 */
public class InvalidPaginationException extends RuntimeException {

    /**
     * Constructs an {@link InvalidPaginationException} with a human-readable message
     * that will be forwarded directly to the API consumer.
     *
     * @param message the validation error message (safe to expose externally)
     */
    public InvalidPaginationException(String message) {
        super(message);
    }
}
