package com.causa.core.services.validation.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.causa.common.constants.LLMConstants;
import com.causa.common.constants.PromptConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.config.AppConfig;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.PromptTemplateLoader;
import com.causa.core.services.validation.AssertionExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * LLM-based assertion extractor.
 *
 * <p>Uses an LLM to intelligently extract atomic assertions from RCA output.
 * More sophisticated than rule-based splitting - understands context and
 * can split complex sentences into multiple assertions.
 *
 * <p>Enabled when: causa.validation.assertion-extractor=llm
 *
 * @since 0.0.1
 */
@ApplicationScoped
@IfBuildProperty(name = "causa.validation.assertion-extractor", stringValue = "llm")
public class LlmAssertionExtractor implements AssertionExtractor {

    private static final CausaLogger log = CausaLogger.getLogger(LlmAssertionExtractor.class);

    private final PromptSender promptSender;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final String provider;

    @Inject
    public LlmAssertionExtractor(
        PromptSender promptSender,
        AppConfig appConfig,
        ObjectMapper objectMapper
    ) {
        this.promptSender = promptSender;
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = new PromptTemplateLoader(PromptConstants.TEMPLATE_PATH_ASSERTION_EXTRACTION);
        this.provider = determineProvider(appConfig.getLlmConfig());
    }

    /**
     * Determines the model type for template selection based on LLM configuration.
     */
    private String determineProvider(com.causa.config.LlmConfigSnapshot config) {
        String provider = config.getProvider();
        String modelName = config.getModelName();

        // Check for BOB/Granite models
        if (!modelName.isEmpty() && (
            modelName.toLowerCase().contains(LLMConstants.ModelNames.BOB) ||
            modelName.toLowerCase().contains(LLMConstants.ModelNames.GRANITE))) {
            return LLMConstants.Provider.IBM_BOB;
        }

        // Check for Ollama provider
        if (LLMConstants.Provider.OLLAMA.equalsIgnoreCase(provider)) {
            return LLMConstants.Provider.OLLAMA;
        }

        // Check for direct Anthropic
        if (LLMConstants.Provider.ANTHROPIC.equalsIgnoreCase(provider)) {
            return LLMConstants.Provider.ANTHROPIC;
        }

        // Default to Vertex AI Anthropic
        return LLMConstants.Provider.VERTEX_AI_ANTHROPIC;
    }

    @Override
    public List<Assertion> extractAssertions(RootCauseAnalysis rca) {
        log.info("Extracting assertions using LLM")
            .field("issueTitle", rca.issueTitle())
            .field("extractorType", "llm")
            .log();

        List<Assertion> allAssertions = new ArrayList<>();

        // Extract from root cause
        if (rca.rootCause() != null && !rca.rootCause().isBlank()) {
            allAssertions.addAll(extractFromText(
                rca.rootCause(),
                Assertion.AssertionSource.ROOT_CAUSE,
                "rootCause"
            ));
        }

        // // Extract from issue description
        // if (rca.issueDescription() != null && !rca.issueDescription().isBlank()) {
        //     allAssertions.addAll(extractFromText(
        //         rca.issueDescription(),
        //         Assertion.AssertionSource.ISSUE_DESCRIPTION,
        //         "issueDescription"
        //     ));
        // }

        // // Extract from technical description
        // if (rca.technicalDescription() != null && !rca.technicalDescription().isBlank()) {
        //     allAssertions.addAll(extractFromText(
        //         rca.technicalDescription(),
        //         Assertion.AssertionSource.TECHNICAL_DESCRIPTION,
        //         "technicalDescription"
        //     ));
        // }

        // // Extract from solutions
        // if (rca.possibleSolutions() != null && !rca.possibleSolutions().isEmpty()) {
        //     for (int i = 0; i < rca.possibleSolutions().size(); i++) {
        //         var solution = rca.possibleSolutions().get(i);
        //         allAssertions.addAll(extractFromText(
        //             solution.solution(),
        //             Assertion.AssertionSource.POSSIBLE_SOLUTIONS,
        //             "possibleSolutions[" + i + "]"
        //         ));
        //     }
        // }

        log.info("LLM assertion extraction completed")
            .field("totalAssertions", allAssertions.size())
            .log();

        return allAssertions;
    }

    @Override
    public List<Assertion> extractFromText(
        String text,
        Assertion.AssertionSource source,
        String relatedField
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        try {
            // Load template for the current model type
            PromptTemplateLoader.PromptTemplate template = promptTemplateLoader.loadTemplate(provider, "");

            // Build prompt using template
            String userPrompt = buildExtractionPrompt(text, source, template);

            // Call LLM
            LLMRequest request = LLMRequest.builder(userPrompt)
                .systemPrompt(template.systemPrompt())
                .temperature(0.1) // Low temperature for consistent extraction
                .maxTokens(2000)
                .build();

            LLMResponse response = promptSender.send(request);

            // Parse JSON response
            List<AssertionDto> dtos = parseAssertionResponse(response.responseText());

            // Convert to Assertion objects
            List<Assertion> assertions = new ArrayList<>();
            for (AssertionDto dto : dtos) {
                Assertion assertion = Assertion.of(
                    generateAssertionId(dto.text),
                    dto.text,
                    parseAssertionType(dto.type),
                    source
                );
                assertions.add(assertion);
            }

            log.debug("Extracted assertions from text using LLM")
                .field("source", source)
                .field("assertionCount", assertions.size())
                .log();

            return assertions;

        } catch (Exception e) {
            log.error("LLM assertion extraction failed, falling back to simple splitting")
                .field("source", source)
                .exception(e)
                .log();

            // Fallback to simple sentence splitting
            return fallbackExtraction(text, source, relatedField);
        }
    }

    /**
     * Builds the extraction prompt for the LLM using template placeholders.
     */
    private String buildExtractionPrompt(
        String text,
        Assertion.AssertionSource source,
        PromptTemplateLoader.PromptTemplate template
    ) {
        String sourceLabel = source.toString().toLowerCase().replace("_", " ");

        return template.userPrompt()
            .replace(PromptConstants.PLACEHOLDER_SOURCE, sourceLabel)
            .replace(PromptConstants.PLACEHOLDER_RCA_TEXT, text);
    }

    /**
     * Parses the LLM JSON response into assertion DTOs.
     */
    private List<AssertionDto> parseAssertionResponse(String responseText) throws Exception {
        // Clean response (remove markdown code blocks if present)
        String jsonText = responseText.trim();
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.substring(7);
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.substring(3);
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.substring(0, jsonText.length() - 3);
        }
        jsonText = jsonText.trim();

        // Parse JSON array
        return objectMapper.readValue(
            jsonText,
            objectMapper.getTypeFactory().constructCollectionType(List.class, AssertionDto.class)
        );
    }

    /**
     * Parses assertion type from string.
     */
    private Assertion.AssertionType parseAssertionType(String typeStr) {
        try {
            return Assertion.AssertionType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown assertion type: " + typeStr + ", defaulting to OBSERVATION")
                .log();
            return Assertion.AssertionType.OBSERVATION;
        }
    }

    /**
     * Generates a unique assertion ID.
     */
    private String generateAssertionId(String text) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        int textHash = Math.abs(text.hashCode() % 10000);
        return String.format("assert-llm-%s-%04d", uuid, textHash);
    }

    /**
     * Fallback to simple extraction if LLM fails.
     */
    private List<Assertion> fallbackExtraction(
        String text,
        Assertion.AssertionSource source,
        String relatedField
    ) {
        // Simple sentence splitting fallback
        String[] sentences = text.split("[.!?]+");
        List<Assertion> assertions = new ArrayList<>();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (!sentence.isEmpty()) {
                assertions.add(Assertion.of(
                    generateAssertionId(sentence),
                    sentence,
                    Assertion.AssertionType.OBSERVATION, // Default type
                    source
                ));
            }
        }

        return assertions;
    }

    /**
     * DTO for parsing LLM JSON response.
     */
    private static class AssertionDto {
        public String text;
        public String type;

        // Jackson needs default constructor
        public AssertionDto() {}

        public AssertionDto(String text, String type) {
            this.text = text;
            this.type = type;
        }
    }
}
