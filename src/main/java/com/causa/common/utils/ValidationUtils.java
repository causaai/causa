package com.causa.common.utils;

/**
 * Validation Utilities
 *
 * <p>Simple validators for config value type checking.
 *
 * @since 0.0.1
 */
public final class ValidationUtils {

    private ValidationUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Checks if a string can be parsed as a valid integer.
     *
     * @param value the string to validate
     * @return true if the value is a valid integer
     */
    public static boolean isValidInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Checks if a string can be parsed as a valid double.
     *
     * @param value the string to validate
     * @return true if the value is a valid double
     */
    public static boolean isValidDouble(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Checks if a string is a valid boolean value.
     * Only accepts "true" or "false" (case-insensitive) to prevent ambiguous values
     * like "yes", "1", or "on" from silently passing through.
     *
     * @param value the string to validate
     * @return true if the value is exactly "true" or "false" (case-insensitive)
     */
    public static boolean isValidBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.strip().toLowerCase();
        return lower.equals("true") || lower.equals("false");
    }
}
