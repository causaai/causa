package com.causa.core.domain;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;

import java.util.Optional;

/**
 * LLM Request
 *
 * <p>Immutable domain model representing a request to an LLM provider.
 * All optional parameters fall back to configuration defaults if not specified.
 *
 * @param prompt The user prompt text (required)
 * @param systemPrompt System instructions for the LLM (optional)
 * @param context Additional context to prepend to the system prompt (optional, e.g., RAG results)
 * @param modelOverride Override the configured default model (optional)
 * @param enableCaching Whether to use prompt caching (optional, defaults to config)
 * @param enableSkills Whether to expose skills to the LLM (optional, defaults to true)
 * @param maxTokens Maximum response tokens (optional, defaults to config)
 * @param temperature Sampling temperature 0.0-1.0 (optional, defaults to config)
 * @since 0.0.1
 */
public record LLMRequest(
    String prompt,
    Optional<String> systemPrompt,
    Optional<String> context,
    Optional<String> modelOverride,
    Optional<Boolean> enableCaching,
    Optional<Boolean> enableSkills,
    Optional<Integer> maxTokens,
    Optional<Double> temperature
) {
    /**
     * Compact constructor with validation.
     */
    public LLMRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be null or blank");
        }
        systemPrompt = systemPrompt != null ? systemPrompt : Optional.empty();
        context = context != null ? context : Optional.empty();
        modelOverride = modelOverride != null ? modelOverride : Optional.empty();
        enableCaching = enableCaching != null ? enableCaching : Optional.empty();
        enableSkills = enableSkills != null ? enableSkills : Optional.of(true);
        maxTokens = maxTokens != null ? maxTokens : Optional.empty();
        temperature = temperature != null ? temperature : Optional.empty();

        // Validate using the normalized parameters
        validateLLMRequest(temperature, maxTokens);
    }

    /**
     * Validates LLM request parameters.
     * Centralized validation method for easy extension in the future.
     *  
     * @param temperature the temperature parameter to validate
     * @param maxTokens the maxTokens parameter to validate
     * @throws LLMException if any parameter is invalid
     */
    private static void validateLLMRequest(Optional<Double> temperature, Optional<Integer> maxTokens) {
        // Validate temperature range
        temperature.ifPresent(t -> {
            if (t < LLMConstants.Validation.MIN_TEMPERATURE || t > LLMConstants.Validation.MAX_TEMPERATURE) {
                throw new LLMException(
                    LLMConstants.ErrorMessages.TEMPERATURE_RANGE_MESSAGE,
                    LLMConstants.ErrorTypes.INVALID_REQUEST_PARAMETERS
                );
            }
        });

        // Validate maxTokens range
        maxTokens.ifPresent(tokens -> {
            if (tokens < LLMConstants.Validation.MIN_MAX_TOKENS) {
                throw new LLMException(
                    LLMConstants.ErrorMessages.MAX_TOKENS_RANGE_MESSAGE,
                    LLMConstants.ErrorTypes.INVALID_REQUEST_PARAMETERS
                );
            }
        });
    }

    /**
     * Creates a minimal request with only a prompt.
     *
     * @param prompt the user prompt
     * @return a new LLMRequest
     */
    public static LLMRequest of(String prompt) {
        return new LLMRequest(prompt, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(true), Optional.empty(), Optional.empty());
    }

    /**
     * Creates a builder for constructing an LLMRequest.
     *
     * @param prompt the user prompt (required)
     * @return a new Builder
     */
    public static Builder builder(String prompt) {
        return new Builder(prompt);
    }

    /**
     * Builder for ergonomic LLMRequest construction.
     */
    public static final class Builder {
        private final String prompt;
        private Optional<String> systemPrompt = Optional.empty();
        private Optional<String> context = Optional.empty();
        private Optional<String> modelOverride = Optional.empty();
        private Optional<Boolean> enableCaching = Optional.empty();
        private Optional<Boolean> enableSkills = Optional.of(true);
        private Optional<Integer> maxTokens = Optional.empty();
        private Optional<Double> temperature = Optional.empty();

        private Builder(String prompt) {
            this.prompt = prompt;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = Optional.ofNullable(systemPrompt);
            return this;
        }

        public Builder context(String context) {
            this.context = Optional.ofNullable(context);
            return this;
        }

        public Builder modelOverride(String modelOverride) {
            this.modelOverride = Optional.ofNullable(modelOverride);
            return this;
        }

        public Builder enableCaching(boolean enableCaching) {
            this.enableCaching = Optional.of(enableCaching);
            return this;
        }

        public Builder enableSkills(boolean enableSkills) {
            this.enableSkills = Optional.of(enableSkills);
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = Optional.of(maxTokens);
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = Optional.of(temperature);
            return this;
        }

        public LLMRequest build() {
            return new LLMRequest(prompt, systemPrompt, context, modelOverride,
                    enableCaching, enableSkills, maxTokens, temperature);
        }
    }
}
