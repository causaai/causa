package com.causa.common.exceptions;

/**
 * Diagnostic Exception
 *
 * <p>Unchecked exception for diagnostic processing failures. Used across the diagnostic module
 * to signal validation, persistence, or processing errors.
 *
 * @since 0.0.1
 */
public class DiagnosticException extends RuntimeException {

    private final String errorType;

    /**
     * Constructs a new DiagnosticException with a message and error type.
     *
     * @param message the error message
     * @param errorType the classification of the error
     */
    public DiagnosticException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }

    /**
     * Constructs a new DiagnosticException with a message, error type, and cause.
     *
     * @param message the error message
     * @param errorType the classification of the error
     * @param cause the underlying cause
     */
    public DiagnosticException(String message, String errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    /**
     * Constructs a new DiagnosticException wrapping another exception.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public DiagnosticException(String message, Throwable cause) {
        this(message, cause != null ? cause.getClass().getSimpleName() : "Unknown", cause);
    }

    /**
     * Gets the error type classification.
     *
     * @return the error type
     */
    public String getErrorType() {
        return errorType;
    }
}
