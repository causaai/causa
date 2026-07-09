package com.causa.core.services;

import com.causa.common.constants.PromptConstants;
import com.causa.config.RcaConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
            Map<String, Object> templates = yaml.load(is);

            Map<String, Object> modelTemplate = (Map<String, Object>) templates.get(modelType);
            if (modelTemplate == null) {
                // Fallback to default if model-specific template not found
                modelTemplate = (Map<String, Object>) templates.get(PromptConstants.DEFAULT_MODEL_TYPE);
                if (modelTemplate == null) {
                    throw new IllegalStateException(
                        String.format("Default prompt template (%s) not found in %s",
                            PromptConstants.DEFAULT_MODEL_TYPE, templatePath)
                    );
                }
            }

            return new PromptTemplate(
                (String) modelTemplate.get(PromptConstants.KEY_NAME),
                (String) modelTemplate.get(PromptConstants.KEY_VERSION),
                (String) modelTemplate.get(PromptConstants.KEY_DESCRIPTION),
                (String) modelTemplate.get(PromptConstants.KEY_SYSTEM_PROMPT),
                (String) modelTemplate.get(PromptConstants.KEY_USER_PROMPT),
                (String) modelTemplate.get(PromptConstants.KEY_VERIFICATION_OBSERVATION),
                (String) modelTemplate.get(PromptConstants.KEY_VERIFICATION_TREND),
                (String) modelTemplate.get(PromptConstants.KEY_VERIFICATION_CAUSALITY),
                (String) modelTemplate.get(PromptConstants.KEY_VERIFICATION_CONFIGURATION),
                (String) modelTemplate.get(PromptConstants.KEY_VERIFICATION_RECOMMENDATION)
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
        String userPrompt,
        String verificationObservation,
        String verificationTrend,
        String verificationCausality,
        String verificationConfiguration,
        String verificationRecommendation
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

        /**
         * Gets verification guidance for a specific assertion type.
         *
         * @param assertionType the assertion type (OBSERVATION, TREND, CAUSALITY, CONFIGURATION, RECOMMENDATION)
         * @return the verification guidance text, or empty string if not available
         */
        public String getVerificationGuidance(String assertionType) {
            return switch (assertionType.toUpperCase()) {
                case "OBSERVATION" -> verificationObservation != null ? verificationObservation : "";
                case "TREND" -> verificationTrend != null ? verificationTrend : "";
                case "CAUSALITY" -> verificationCausality != null ? verificationCausality : "";
                case "CONFIGURATION" -> verificationConfiguration != null ? verificationConfiguration : "";
                case "RECOMMENDATION" -> verificationRecommendation != null ? verificationRecommendation : "";
                default -> "";
            };
        }
    }
}
