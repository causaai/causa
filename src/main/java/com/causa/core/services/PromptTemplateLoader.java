package com.causa.core.services;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.yaml.snakeyaml.Yaml;

import com.causa.common.constants.PromptConstants;
import com.causa.config.RcaConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Prompt Template Loader
 *
 * <p>Loads and caches YAML-based prompt templates for different LLM models.
 * Supports model-specific prompt variations (vertex-ai-anthropic, direct-anthropic, bob, ollama)
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class PromptTemplateLoader {

    private final String templatePath;
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    @Inject
    public PromptTemplateLoader(RcaConfig rcaConfig) {
        String configuredPath = rcaConfig.templatePath();
        // Normalize path - ensure it has leading "/" for classloader resource lookup
        this.templatePath = configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }

    /**
     * Constructor for direct path usage (non-CDI, for assertion extraction/analysis).
     *
     * @param templatePath the direct path to the template file
     */
    public PromptTemplateLoader(String templatePath) {
        // Normalize path - ensure it has leading "/" for classloader resource lookup
        this.templatePath = templatePath.startsWith("/") ? templatePath : "/" + templatePath;
    }

    /**
     * Loads a prompt template for the specified model type.
     *
     * @param modelType the model type (default, bob, ollama, etc.)
     * @return the prompt template
     * @throws IllegalArgumentException if template not found for model type
     */
    public PromptTemplate loadTemplate(String modelType) {
        return templateCache.computeIfAbsent(modelType, this::loadTemplateFromYaml);
    }

    /**
     * Loads template from YAML file.
     */
    @SuppressWarnings("unchecked")
    private PromptTemplate loadTemplateFromYaml(String modelType) {
        try (InputStream is = getClass().getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new IllegalStateException(
                    String.format("Prompt template file not found at configured path: %s (resolved as: %s)",
                        templatePath, getClass().getResource(templatePath))
                );
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);

            List<Map<String, Object>> prompts =
                (List<Map<String, Object>>) root.get(PromptConstants.KEY_PROMPTS);
            if (prompts == null || prompts.isEmpty()) {
                throw new IllegalStateException(
                    String.format("No prompts list found in %s", templatePath));
            }

            Map<String, Object> modelTemplate = null;
            Map<String, Object> defaultTemplate = null;

            for (Map<String, Object> entry : prompts) {
                List<String> models = (List<String>) entry.get(PromptConstants.KEY_MODELS);
                if (models == null) continue;

                if (models.contains(modelType)) {
                    modelTemplate = entry;
                    break;
                }
                if (defaultTemplate == null && models.contains(PromptConstants.DEFAULT_MODEL_TYPE)) {
                    defaultTemplate = entry;
                }
            }

            if (modelTemplate == null) {
                if (defaultTemplate != null) {
                    modelTemplate = defaultTemplate;
                } else {
                    throw new IllegalStateException(
                        String.format("No prompt template configured for model type '%s' in %s",
                            modelType, templatePath));
                }
            }

            return new PromptTemplate(
                (String) modelTemplate.get(PromptConstants.KEY_NAME),
                (String) modelTemplate.get(PromptConstants.KEY_VERSION),
                (String) modelTemplate.get(PromptConstants.KEY_DESCRIPTION),
                (String) modelTemplate.get(PromptConstants.KEY_SYSTEM_PROMPT),
                (String) modelTemplate.get(PromptConstants.KEY_USER_PROMPT)
            );

        } catch (Exception e) {
            throw new IllegalStateException("Failed to load prompt template for model type: " + modelType, e);
        }
    }

    /**
     * Prompt Template Record
     */
    public record PromptTemplate(
        String name,
        String version,
        String description,
        String systemPrompt,
        String userPrompt
    ) {
        /**
         * Renders the user prompt by replacing the context placeholder.
         *
         * @param context the MCP context string (includes all signal data)
         * @return the rendered prompt
         */
        public String render(String context) {
            return userPrompt.replace(PromptConstants.PLACEHOLDER_CONTEXT, context);
        }
    }
}
