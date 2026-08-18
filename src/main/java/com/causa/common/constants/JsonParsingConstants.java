package com.causa.common.constants;

import java.util.regex.Pattern;

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

    /**
     * Skips an optional opening code fence ({@code ```<lang>\n}) and any prose preamble,
     * then captures from the first {@code {}} to end-of-string (group 1) for Jackson to parse.
     * Returns no match if no {@code {}} is present.
     */
    public static final Pattern JSON_OBJECT_PATTERN =
            Pattern.compile("(?:```[^\\n]*\\n)?[^{]*(\\{.*)", Pattern.DOTALL);
}
