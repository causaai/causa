package com.causa.common.constants;

/**
 * Model Type Enum
 *
 * <p>Defines supported LLM model types for prompt template selection.
 * Maps provider identifiers to their corresponding template names using
 * {@link LLMConstants.Provider} for consistency.
 *
 * <p><strong>Design Note:</strong> Uses LLMConstants.Provider values directly to ensure
 * provider strings are consistent across configuration, validation, and prompt selection.
 *
 * @since 0.0.1
 */
public enum ModelType {
    /** Vertex AI Anthropic provider (Claude via Google Cloud) */
    VERTEX_AI_ANTHROPIC(LLMConstants.Provider.VERTEX_AI_ANTHROPIC),

    /** Direct Anthropic provider (Claude API) */
    DIRECT_ANTHROPIC(LLMConstants.Provider.ANTHROPIC),

    /** IBM BOB provider (Granite models) */
    BOB(LLMConstants.Provider.IBM_BOB),

    /** Ollama provider (local models) */
    OLLAMA(LLMConstants.Provider.OLLAMA);

    private final String templateName;

    ModelType(String templateName) {
        this.templateName = templateName;
    }

    /**
     * Returns the template name for this model type.
     *
     * @return the template name
     */
    public String getTemplateName() {
        return templateName;
    }

    /**
     * Determines model type from provider and model name.
     *
     * <p>Maps runtime LLM provider configuration to the appropriate prompt template.
     * Uses {@link LLMConstants.Provider} values for consistent provider matching.
     *
     * <p><strong>Fallback Strategy:</strong> If the provider doesn't match any known type,
     * falls back to PromptConstants.DEFAULT_MODEL_TYPE (vertex-ai-anthropic). This ensures
     * we always have a valid prompt template while maintaining a single source of truth
     * for the default provider.
     *
     * @param provider the LLM provider from configuration (e.g., "vertex-ai-anthropic", "anthropic", "bob")
     * @param modelName the model name (e.g., "claude-sonnet-4-6", "granite-13b")
     * @return the corresponding ModelType
     */
    public static ModelType from(String provider, String modelName) {
        // Check if model name indicates BOB/Granite (override provider if model name is definitive)
        if (modelName != null) {
            String lowerModelName = modelName.toLowerCase();
            if (lowerModelName.contains(LLMConstants.ModelNames.BOB) ||
                lowerModelName.contains(LLMConstants.ModelNames.GRANITE)) {
                return BOB;
            }
        }

        // Match by provider using LLMConstants.Provider for consistency
        if (LLMConstants.Provider.VERTEX_AI_ANTHROPIC.equalsIgnoreCase(provider)) {
            return VERTEX_AI_ANTHROPIC;
        }
        if (LLMConstants.Provider.ANTHROPIC.equalsIgnoreCase(provider)) {
            return DIRECT_ANTHROPIC;
        }
        if (LLMConstants.Provider.IBM_BOB.equalsIgnoreCase(provider)) {
            return BOB;
        }
        if (LLMConstants.Provider.OLLAMA.equalsIgnoreCase(provider)) {
            return OLLAMA;
        }

        // Fallback: Use default from PromptConstants (single source of truth)
        // This happens when provider is unknown or null
        return VERTEX_AI_ANTHROPIC;
    }
}
