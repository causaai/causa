package com.causa.common.constants;

/**
 * JSON Parsing Constants
 *
 * <p>Constants used for parsing and cleaning JSON responses from LLMs.
 *
 * @since 0.0.1
 */
public final class JsonParsingConstants {

    private JsonParsingConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * JSON code block prefix (markdown format)
     */
    public static final String JSON_CODE_BLOCK_PREFIX = "```json";

    /**
     * Generic code block prefix (markdown format)
     */
    public static final String CODE_BLOCK_PREFIX = "```";

    /**
     * Length of JSON code block prefix
     */
    public static final int JSON_CODE_BLOCK_PREFIX_LENGTH = JSON_CODE_BLOCK_PREFIX.length();

    /**
     * Length of generic code block prefix
     */
    public static final int CODE_BLOCK_PREFIX_LENGTH = CODE_BLOCK_PREFIX.length();
}
