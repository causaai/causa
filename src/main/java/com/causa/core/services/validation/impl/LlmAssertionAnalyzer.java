package com.causa.core.services.validation.impl;

import com.causa.common.constants.JsonParsingConstants;
import com.causa.common.constants.LLMConstants;
import com.causa.common.constants.PromptConstants;
import com.causa.common.constants.ValidationConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.PromptTemplateLoader;
import com.causa.core.services.validation.AssertionAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.arc.properties.IfBuildProperty;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

/**
 * LLM-based assertion analyzer.
 *
 * <p>Uses an LLM to intelligently verify assertions by:
 * <ul>
 *   <li>Asking targeted questions about the assertion</li>
 *   <li>Searching for evidence in the diagnostic context</li>
 *   <li>Evaluating the strength of evidence</li>
 *   <li>Providing confidence scores and explanations</li>
 * </ul>
 *
 * <p>Enabled when: causa.validation.assertion-analyzer=llm
 *
 * @since 0.0.1
 */
@ApplicationScoped
@IfBuildProperty(name = "causa.validation.assertion-analyzer", stringValue = "llm", enableIfMissing = true)
public class LlmAssertionAnalyzer implements AssertionAnalyzer {

    private static final CausaLogger log = CausaLogger.getLogger(LlmAssertionAnalyzer.class);

    private final PromptSender promptSender;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final String provider;
    private final ExecutorService executorService;

    @Inject
    public LlmAssertionAnalyzer(
        PromptSender promptSender,
        AppConfig appConfig,
        ObjectMapper objectMapper,
        @ConfigProperty(name = "causa.validation.assertion-analyzer.parallel-threads")
        int parallelThreads
    ) {
        this.promptSender = promptSender;
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = new PromptTemplateLoader(PromptConstants.TEMPLATE_PATH_ASSERTION_ANALYSIS);
        this.provider = determineProvider(appConfig.getLlmConfig());
        this.executorService = Executors.newFixedThreadPool(parallelThreads);
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
    public ValidationResult analyze(Assertion assertion, String diagnosticContext) {
        log.debug(LogMessages.Validation.ASSERTION_ANALYZING)
            .field("assertionId", assertion.id())
            .field("assertionType", assertion.type())
            .field("assertionText", assertion.text())
            .log();

        // Skip validation for recommendations
        if (assertion.type() == Assertion.AssertionType.RECOMMENDATION) {
            return ValidationResult.unknown(
                assertion,
                LogMessages.Validation.ASSERTION_SKIP_RECOMMENDATION
            );
        }

        try {
            // Load template for the current model type
            PromptTemplateLoader.PromptTemplate template = promptTemplateLoader.loadTemplate(provider, "");

            // Build analysis prompt using template
            String userPrompt = buildAnalysisPrompt(assertion, diagnosticContext, template);

            // Call LLM
            // Skills disabled — the assertion analyzer has all context it needs in
            // diagnosticContext; activating skills adds unnecessary tool round-trips
            // (3 per assertion × N assertions) with no benefit for validation.
            LLMRequest request = LLMRequest.builder(userPrompt)
                .systemPrompt(template.systemPrompt())
                .temperature(0.2) // Low temperature for consistent analysis
                .maxTokens(3000)  // Allow detailed analysis
                .enableSkills(false)
                .build();

            LLMResponse response = promptSender.send(request);

            // Parse response
            AnalysisResult result = parseAnalysisResponse(response.responseText());

            // Convert to ValidationResult
            ValidationResult validationResult = toValidationResult(assertion, result);

            log.info(LogMessages.Validation.ASSERTION_ANALYSIS_COMPLETED)
                .field("assertionId", assertion.id())
                .field("status", validationResult.status())
                .field("confidence", validationResult.confidence())
                .field("evidenceCount", validationResult.evidenceCount())
                .log();

            return validationResult;

        } catch (Exception e) {
            log.error(LogMessages.Validation.ASSERTION_ANALYSIS_FAILED)
                .field("assertionId", assertion.id())
                .exception(e)
                .log();

            // Return unknown on error
            return ValidationResult.unknown(
                assertion,
                "Analysis failed: " + e.getMessage()
            );
        }
    }

    @Override
    public List<ValidationResult> analyzeAll(
        List<Assertion> assertions,
        String diagnosticContext
    ) {
        log.info(LogMessages.Validation.ASSERTION_BATCH_START)
            .field("totalAssertions", assertions.size())
            .log();

        List<CompletableFuture<ValidationResult>> futures = assertions.stream()
            .map(assertion -> CompletableFuture.supplyAsync(
                () -> analyze(assertion, diagnosticContext), executorService))
            .toList();

        List<ValidationResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        log.info(LogMessages.Validation.ASSERTION_BATCH_COMPLETED)
            .field("totalAssertions", assertions.size())
            .field("supported", results.stream().filter(r -> r.status() == ValidationResult.ValidationStatus.SUPPORTED).count())
            .field("unsupported", results.stream().filter(r -> r.status() == ValidationResult.ValidationStatus.UNSUPPORTED).count())
            .log();

        return results;
    }

    /**
     * Builds the analysis prompt for the LLM using template placeholders.
     */
    private String buildAnalysisPrompt(
        Assertion assertion,
        String diagnosticContext,
        PromptTemplateLoader.PromptTemplate template
    ) {
        // Get verification guidance from template for this assertion type
        String verificationGuidance = template.getVerificationGuidance(assertion.type().name());

        // Replace placeholders in the template
        return template.userPrompt()
            .replace(PromptConstants.PLACEHOLDER_ASSERTION_TEXT, assertion.text())
            .replace(PromptConstants.PLACEHOLDER_ASSERTION_TYPE, assertion.type().name())
            .replace(PromptConstants.PLACEHOLDER_ASSERTION_SOURCE, assertion.source().name())
            .replace(PromptConstants.PLACEHOLDER_VERIFICATION_GUIDANCE, verificationGuidance)
            .replace(PromptConstants.PLACEHOLDER_DIAGNOSTIC_CONTEXT, diagnosticContext);
    }

    /**
     * Parses the LLM analysis response, extracting JSON even if surrounded by text.
     */
    private AnalysisResult parseAnalysisResponse(String responseText) throws Exception {
        // Extract the outermost JSON object from the response.
        // The pattern handles in order:
        //   1. an optional opening code fence (```<lang>\n) — skipped as a unit
        //   2. any leading prose before the first '{' — consumed by [^{]*
        //   3. trailing text after the last '}' (closing fence, prose) — excluded by greedy .*}
        Matcher jsonMatcher = JsonParsingConstants.JSON_OBJECT_PATTERN.matcher(responseText);
        if (!jsonMatcher.find()) {
            throw new IllegalArgumentException(LogMessages.Validation.ASSERTION_NO_JSON);
        }
        return objectMapper.readValue(jsonMatcher.group(1), AnalysisResult.class);
    }

    /**
     * Converts AnalysisResult to ValidationResult.
     */
    private ValidationResult toValidationResult(Assertion assertion, AnalysisResult result) {
        // Parse status
        ValidationResult.ValidationStatus status = ValidationResult.ValidationStatus.valueOf(
            result.status.toUpperCase()
        );

        // Convert evidence
        List<Evidence> evidenceList = new ArrayList<>();
        if (result.evidence != null) {
            for (EvidenceDto dto : result.evidence) {
                Evidence evidence = Evidence.of(
                    dto.source != null ? dto.source : "llm-analysis",
                    parseEvidenceType(dto.type),
                    dto.snippet,
                    dto.relevance
                );
                evidenceList.add(evidence);
            }
        }

        // Build explanation
        StringBuilder explanation = new StringBuilder();
        if (result.reasoning != null) {
            explanation.append(result.reasoning);
        }
        if (result.questionsAsked != null && !result.questionsAsked.isEmpty()) {
            explanation.append("\n\nQuestions asked:\n");
            for (String question : result.questionsAsked) {
                explanation.append("- ").append(question).append("\n");
            }
        }

        // Return validation result
        return new ValidationResult(
            assertion,
            status,
            result.confidence,
            status == ValidationResult.ValidationStatus.SUPPORTED ||
            status == ValidationResult.ValidationStatus.PARTIALLY_SUPPORTED
                ? evidenceList : List.of(),
            status == ValidationResult.ValidationStatus.UNSUPPORTED
                ? evidenceList : List.of(),
            Optional.of(explanation.toString().trim())
        );
    }

    /**
     * Parses evidence type from string.
     */
    private Evidence.EvidenceType parseEvidenceType(String typeStr) {
        if (typeStr == null) {
            return Evidence.EvidenceType.OTHER;
        }
        try {
            return Evidence.EvidenceType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Evidence.EvidenceType.OTHER;
        }
    }

    /**
     * DTO for LLM analysis result.
     */
    private static class AnalysisResult {
        public String status;
        public double confidence;
        public List<EvidenceDto> evidence;
        public String reasoning;
        public List<String> questionsAsked;

        // Jackson needs default constructor
        public AnalysisResult() {}
    }

    /**
     * DTO for evidence in analysis result.
     */
    private static class EvidenceDto {
        public String snippet;
        public String source;
        public double relevance;
        public String type;

        // Jackson needs default constructor
        public EvidenceDto() {}
    }
}
