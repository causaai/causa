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
     * Default model type fallback - uses the same value as LLMConstants.Provider.VERTEX_AI_ANTHROPIC
     * to maintain consistency across the codebase.
     *
     * <p>This is used only when a model-specific prompt template is not found in YAML.
     * The actual LLM provider is determined at runtime from LLMConfig.
     */
    public static final String DEFAULT_MODEL_TYPE = LLMConstants.Provider.VERTEX_AI_ANTHROPIC;

    /**
     * Template placeholder strings
     */
    public static final String PLACEHOLDER_CONTEXT = "{{context}}";
    public static final String PLACEHOLDER_ASSERTION_TEXT = "{{assertion_text}}";
    public static final String PLACEHOLDER_ASSERTION_TYPE = "{{assertion_type}}";
    public static final String PLACEHOLDER_ASSERTION_SOURCE = "{{assertion_source}}";
    public static final String PLACEHOLDER_VERIFICATION_GUIDANCE = "{{verification_guidance}}";
    public static final String PLACEHOLDER_DIAGNOSTIC_CONTEXT = "{{diagnostic_context}}";

    /**
     * Template paths
     */
    public static final String TEMPLATE_PATH_ASSERTION_ANALYSIS = "prompts/assertion-analysis-prompt-template.yml";
    public static final String TEMPLATE_PATH_ASSERTION_EXTRACTION = "prompts/assertion-extraction-prompt-template.yml";

    public static final String PLACEHOLDER_SOURCE = "{{source}}";
    public static final String PLACEHOLDER_RCA_TEXT = "{{rca_text}}";
}
