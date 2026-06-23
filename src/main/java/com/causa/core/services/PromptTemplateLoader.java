package com.causa.core.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt Template Loader
 *
 * <p>Loads and caches YAML-based prompt templates for different LLM models.
 * Supports model-specific prompt variations (default, bob, ollama, etc.)
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class PromptTemplateLoader {

    private static final String TEMPLATE_PATH = "/prompts/rca-prompt-template.yml";
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

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
        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Prompt template file not found: " + TEMPLATE_PATH);
            }

            Yaml yaml = new Yaml();
            Map<String, Object> templates = yaml.load(is);

            Map<String, Object> modelTemplate = (Map<String, Object>) templates.get(modelType);
            if (modelTemplate == null) {
                // Fallback to default if model-specific template not found
                modelTemplate = (Map<String, Object>) templates.get("default");
                if (modelTemplate == null) {
                    throw new IllegalStateException("Default prompt template not found in " + TEMPLATE_PATH);
                }
            }

            return new PromptTemplate(
                (String) modelTemplate.get("name"),
                (String) modelTemplate.get("version"),
                (String) modelTemplate.get("description"),
                (String) modelTemplate.get("system_prompt"),
                (String) modelTemplate.get("user_prompt")
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
         * Renders the user prompt by replacing placeholders with actual values.
         *
         * @param alertName     the alert name
         * @param severity      the alert severity (will be converted to string)
         * @param podName       the pod name
         * @param namespace     the namespace
         * @param containerName the container name
         * @param context       the MCP context string
         * @return the rendered prompt
         */
        public String render(String alertName, Object severity, String podName,
                             String namespace, String containerName, String context) {
            return userPrompt
                .replace("{{alertName}}", alertName)
                .replace("{{severity}}", severity != null ? severity.toString() : "unknown")
                .replace("{{podName}}", podName)
                .replace("{{namespace}}", namespace)
                .replace("{{containerName}}", containerName != null ? containerName : "N/A")
                .replace("{{context}}", context);
        }
    }
}
