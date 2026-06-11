package com.causa.common.exceptions;

/**
 * LLM Exception
 *
 * <p>Unchecked exception for LLM operation failures. Wraps underlying provider exceptions
 * to prevent implementation details from leaking into the domain layer.
 *
 * @since 0.0.1
 */
public class LLMException extends RuntimeException {

    private final String errorType;

    /**
     * Constructs a new LLMException with a message and error type.
     *
     * @param message the error message
     * @param errorType the classification of the error
     */
    public LLMException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }

    /**
     * Constructs a new LLMException with a message, error type, and cause.
     *
     * @param message the error message
     * @param errorType the classification of the error
     * @param cause the underlying cause
     */
    public LLMException(String message, String errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    /**
     * Constructs a new LLMException wrapping another exception.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public LLMException(String message, Throwable cause) {
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
