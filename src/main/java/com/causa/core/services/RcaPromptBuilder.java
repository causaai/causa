package com.causa.core.services;

import com.causa.common.constants.ModelType;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.Alert;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * RCA Prompt Builder
 *
 * <p>Builds the Root Cause Analysis prompt by injecting MCP context
 * (pod status, events, logs) into YAML-based prompt templates.
 *
 * <p>Supports model-specific prompt variations loaded from
 * {@code /prompts/rca-prompt-template.yml}.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class RcaPromptBuilder {

    private final PromptTemplateLoader templateLoader;
    private final LlmConfigCache llmConfigCache;

    @Inject
    public RcaPromptBuilder(PromptTemplateLoader templateLoader, LlmConfigCache llmConfigCache) {
        this.templateLoader = templateLoader;
        this.llmConfigCache = llmConfigCache;
    }

    /**
     * Builds the complete RCA prompt with MCP context.
     *
     * <p>Loads model-specific prompt template from YAML and renders it with context data.
     * Alert details are embedded in the MCP context, not passed separately.
     *
     * @param alert the alert to analyze (unused, kept for API compatibility)
     * @param mcpContext the collected MCP context as a string (includes all signal data)
     * @return the complete RCA prompt
     */
    public String buildPrompt(Alert alert, String mcpContext) {
        String provider  = llmConfigCache.getActive()
            .map(a -> a.getProvider().name().toLowerCase()).orElse("");
        String modelName = llmConfigCache.getActive()
            .filter(a -> a.getModels() != null && !a.getModels().isEmpty())
            .map(a -> a.getModels().get(0)).orElse("");

        ModelType modelType = determineModelType(provider, modelName);
        PromptTemplateLoader.PromptTemplate template =
            templateLoader.loadTemplate(modelType.getTemplateName(), modelName);
        return template.render(mcpContext);
    }

    /**
     * Gets the system prompt for the configured model.
     *
     * @return the system prompt
     */
    public String getSystemPrompt() {
        String provider  = llmConfigCache.getActive()
            .map(a -> a.getProvider().name().toLowerCase()).orElse("");
        String modelName = llmConfigCache.getActive()
            .filter(a -> a.getModels() != null && !a.getModels().isEmpty())
            .map(a -> a.getModels().get(0)).orElse("");

        ModelType modelType = determineModelType(provider, modelName);
        PromptTemplateLoader.PromptTemplate template =
            templateLoader.loadTemplate(modelType.getTemplateName(), modelName);
        return template.systemPrompt();
    }

    /**
     * Determines the template model type based on provider and model name.
     *
     * @param provider  the LLM provider
     * @param modelName the model name
     * @return the ModelType enum
     */
    private ModelType determineModelType(String provider, String modelName) {
        return ModelType.from(provider, modelName);
    }
}
