package com.causa.common.exceptions;

/**
 * MCP Config Load Exception
 *
 * <p>Unchecked exception for MCP configuration loading failures — missing file, invalid JSON, or
 * Bean Validation failures against {@code mcp.json}.
 *
 * @since 0.0.1
 */
public class McpConfigLoadException extends RuntimeException {

    private final String errorType;

    /**
     * Constructs a new McpConfigLoadException with a message and error type.
     *
     * @param message the error message
     * @param errorType the classification of the error
     */
    public McpConfigLoadException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }

    /**
     * Constructs a new McpConfigLoadException with a message, error type, and cause.
     *
     * @param message the error message
     * @param errorType the classification of the error
     * @param cause the underlying cause
     */
    public McpConfigLoadException(String message, String errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    /**
     * Constructs a new McpConfigLoadException wrapping another exception.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public McpConfigLoadException(String message, Throwable cause) {
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
