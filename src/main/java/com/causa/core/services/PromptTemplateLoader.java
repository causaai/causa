package com.causa.core.services;

import com.causa.common.constants.LLMConstants;
import com.causa.common.constants.PromptConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.config.RcaConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PromptTemplateLoader {

    private static final CausaLogger log = CausaLogger.getLogger(PromptTemplateLoader.class);

    private final String templatePath;
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    @Inject
    public PromptTemplateLoader(RcaConfig rcaConfig) {
        String configuredPath = rcaConfig.templatePath();
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
     *  Loads a prompt template for the specified provider and model name.
     * @param provider  the LLM provider (e.g., "vertex-ai-anthropic", "anthropic", "bob", "ollama")
     * @param modelName the model name (e.g., "claude-sonnet-4-6"); may be empty for provider-only lookups like BOB
     * @return the prompt template
     * @throws IllegalStateException if template not found for provider
     */
    public PromptTemplate loadTemplate(String provider, String modelName) {
        return templateCache.computeIfAbsent(provider, p -> loadTemplateFromYaml(p, modelName));
    }

    private PromptTemplate loadTemplateFromYaml(String provider, String modelName) {
        try (InputStream is = getClass().getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new IllegalStateException(
                    String.format("Prompt template file not found at configured path: %s", templatePath));
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);

            Object promptsRaw = root.get(PromptConstants.KEY_PROMPTS);
            if (!(promptsRaw instanceof List<?> promptsList) || promptsList.isEmpty()) {
                throw new IllegalStateException(
                    String.format("No prompts list found in %s", templatePath));
            }

            Map<String, Object> matchedEntry = null;
            Map<String, Object> defaultEntry = null;

            for (Object item : promptsList) {
                if (!(item instanceof Map<?, ?> entry)) {
                    log.warn("Skipping non-map entry in prompts list").log();
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> entryMap = (Map<String, Object>) entry;

                Object providersRaw = entryMap.get(PromptConstants.KEY_PROVIDERS);
                if (!(providersRaw instanceof List<?> providersList)) {
                    log.warn("Skipping prompt entry with missing or invalid 'providers' field").log();
                    continue;
                }

                List<String> providers = validateStringList(providersList, "providers");
                if (providers.isEmpty()) {
                    log.warn("Skipping prompt entry with empty providers list").log();
                    continue;
                }

                if (providers.contains(provider)) {
                    matchedEntry = entryMap;
                    break;
                }
                if (defaultEntry == null && providers.contains(PromptConstants.DEFAULT_MODEL_TYPE)) {
                    defaultEntry = entryMap;
                }
            }

            if (matchedEntry == null) {
                if (defaultEntry != null) {
                    log.warn("Provider '{}' not found in any prompt entry, using default")
                        .field("provider", provider)
                        .log();
                    matchedEntry = defaultEntry;
                } else {
                    throw new IllegalStateException(
                        String.format("No prompt template configured for provider '%s' in %s",
                            provider, templatePath));
                }
            }

            validateModelName(matchedEntry, provider, modelName);

            return new PromptTemplate(
                (String) matchedEntry.get(PromptConstants.KEY_NAME),
                (String) matchedEntry.get(PromptConstants.KEY_VERSION),
                (String) matchedEntry.get(PromptConstants.KEY_DESCRIPTION),
                (String) matchedEntry.get(PromptConstants.KEY_SYSTEM_PROMPT),
                (String) matchedEntry.get(PromptConstants.KEY_USER_PROMPT),
                (String) matchedEntry.get(PromptConstants.KEY_VERIFICATION_OBSERVATION),
                (String) matchedEntry.get(PromptConstants.KEY_VERIFICATION_TREND),
                (String) matchedEntry.get(PromptConstants.KEY_VERIFICATION_CAUSALITY),
                (String) matchedEntry.get(PromptConstants.KEY_VERIFICATION_CONFIGURATION),
                (String) matchedEntry.get(PromptConstants.KEY_VERIFICATION_RECOMMENDATION)
            );

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load prompt template for provider: " + provider, e);
        }
    }

    private void validateModelName(Map<String, Object> entry, String provider, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }

        if (LLMConstants.Provider.IBM_BOB.equalsIgnoreCase(provider)) {
            log.info("BOB provider does not use model name, skipping model validation")
                .field("provider", provider)
                .field("modelName", modelName)
                .log();
            return;
        }

        Object modelsRaw = entry.get(PromptConstants.KEY_MODELS);
        if (!(modelsRaw instanceof List<?> modelsList)) {
            log.info("No models list defined for provider '{}', accepting any model")
                .field("provider", provider)
                .log();
            return;
        }

        List<String> models = validateStringList(modelsList, "models");
        if (models.isEmpty()) {
            return;
        }

        boolean matched = models.stream().anyMatch(pattern -> matchesModelPattern(pattern, modelName));
        if (!matched) {
            log.warn("Model '{}' not in configured models list for provider '{}'. Configured models: {}")
                .field("modelName", modelName)
                .field("provider", provider)
                .field("configuredModels", models.toString())
                .log();
        }
    }

    private boolean matchesModelPattern(String pattern, String modelName) {
        if (pattern.endsWith("*")) {
            return modelName.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(modelName);
    }

    private List<String> validateStringList(List<?> rawList, String fieldName) {
        return rawList.stream()
            .filter(item -> {
                if (item == null) {
                    log.warn("Skipping null entry in '{}' list").field("field", fieldName).log();
                    return false;
                }
                if (!(item instanceof String)) {
                    log.warn("Skipping non-string entry in '{}' list: {}")
                        .field("field", fieldName)
                        .field("type", item.getClass().getSimpleName())
                        .log();
                    return false;
                }
                if (((String) item).isBlank()) {
                    log.warn("Skipping blank entry in '{}' list").field("field", fieldName).log();
                    return false;
                }
                return true;
            })
            .map(item -> (String) item)
            .toList();
    }

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
