package com.causa.common.constants;

/**
 * Model Type Enum
 *
 * <p>Defines supported LLM model types for prompt template selection.
 * Maps provider identifiers to their corresponding template names.
 *
 * @since 0.0.1
 */
public enum ModelType {
    VERTEX_AI_ANTHROPIC(LLMConstants.Provider.VERTEX_AI_ANTHROPIC),
    DIRECT_ANTHROPIC(LLMConstants.Provider.ANTHROPIC),
    BOB(LLMConstants.Provider.IBM_BOB);

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
     * @param provider the LLM provider
     * @param modelName the model name
     * @return the corresponding ModelType
     */
    public static ModelType from(String provider, String modelName) {
        // Check if model name indicates BOB/Granite
        if (modelName != null) {
            String lowerModelName = modelName.toLowerCase();
            if (lowerModelName.contains(LLMConstants.ModelNames.BOB) ||
                lowerModelName.contains(LLMConstants.ModelNames.GRANITE)) {
                return BOB;
            }
        }

        // Match by provider
        if (LLMConstants.Provider.VERTEX_AI_ANTHROPIC.equalsIgnoreCase(provider)) {
            return VERTEX_AI_ANTHROPIC;
        }
        if (LLMConstants.Provider.ANTHROPIC.equalsIgnoreCase(provider)) {
            return DIRECT_ANTHROPIC;
        }

        // Default fallback
        return VERTEX_AI_ANTHROPIC;
    }
}
