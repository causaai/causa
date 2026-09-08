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
     * Extracts the outermost JSON object from an LLM response. Handles three cases in order:
     * <ol>
     *   <li>Optional opening code fence ({@code ```<lang>\n}) — skipped as a unit</li>
     *   <li>Any leading prose preamble before the first {@code {} — skipped by {@code [^{]*}</li>
     *   <li>Trailing text after the last {@code }} (closing fence, prose) — excluded by greedy {@code .*\}}</li>
     * </ol>
     * Returns no match if no {@code {}} pair is present.
     */
    public static final Pattern JSON_OBJECT_PATTERN =
            Pattern.compile("(?:```[^\\n]*\\n)?[^{]*(\\{.*})", Pattern.DOTALL);
}
