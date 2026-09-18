package com.causa.common.exceptions;

/**
 * Unchecked exception for external-config and LLM-config operation failures.
 *
 * <p>Thrown by auth-config validators when required fields are missing or blank,
 * and by the settings service layer for constraint violations (e.g. unknown platform,
 * deleting the active LLM provider).
 *
 * @since 0.0.3
 */
public class ConfigException extends RuntimeException {

    private final String errorType;

    public ConfigException(String message, String errorType) {
        super(message);
        this.errorType = errorType;
    }

    public ConfigException(String message, String errorType, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ConfigException(String message, Throwable cause) {
        this(message, cause != null ? cause.getClass().getSimpleName() : "Unknown", cause);
    }

    public String getErrorType() {
        return errorType;
    }
}
