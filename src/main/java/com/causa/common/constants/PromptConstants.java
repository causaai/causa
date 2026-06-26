package com.causa.common.constants;

/**
 * Prompt Template Constants
 *
 * <p>Constants used for prompt template loading and rendering.
 *
 * @since 0.0.1
 */
public final class PromptConstants {

    private PromptConstants() {
        // Utility class - prevent instantiation
    }

    /**
     * YAML template keys
     */
    public static final String KEY_SYSTEM_PROMPT = "system_prompt";
    public static final String KEY_USER_PROMPT = "user_prompt";
    public static final String KEY_NAME = "name";
    public static final String KEY_VERSION = "version";
    public static final String KEY_DESCRIPTION = "description";

    /**
     * Default model type fallback
     */
    public static final String DEFAULT_MODEL_TYPE = "vertex-ai-anthropic";

    /**
     * Template placeholder strings
     */
    public static final String PLACEHOLDER_CONTEXT = "{{context}}";
}
