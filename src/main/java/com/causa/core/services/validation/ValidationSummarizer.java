package com.causa.core.services.validation;

import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.validation.DualValidationResult;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.rules.HypothesisValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * Validation Summarizer.
 *
 * <p>Generates user-friendly, plain-language summaries of validation results using LLM.
 * Converts technical validation data into explanations that end-users can understand.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ValidationSummarizer {

    private static final CausaLogger log = CausaLogger.getLogger(ValidationSummarizer.class);

    private final PromptSender promptSender;

    @Inject
    public ValidationSummarizer(PromptSender promptSender) {
        this.promptSender = promptSender;
    }

    /**
     * Generate user-friendly summary of final verdict.
     *
     * @param dualValidation dual validation result with both paths
     * @param issueTitle the RCA issue title for context
     * @return plain-language summary explaining the validation outcome
     */
    public String generateFinalVerdictSummary(
        DualValidationResult dualValidation,
        String issueTitle
    ) {
        log.info("Generating user-friendly validation summary")
            .field("finalStatus", dualValidation.finalVerdict().status())
            .field("confidence", dualValidation.finalVerdict().confidence())
            .log();

        try {
            String prompt = buildSummaryPrompt(dualValidation, issueTitle);

            LLMRequest request = LLMRequest.builder(prompt)
                .maxTokens(200)
                .temperature(0.3)
                .enableSkills(false)
                .build();

            LLMResponse response = promptSender.send(request);

            if (response.responseText() != null && !response.responseText().isBlank()) {
                String summary = response.responseText().trim();
                log.info("User-friendly summary generated")
                    .field("summaryLength", summary.length())
                    .log();
                return summary;
            } else {
                log.warn("Failed to generate user-friendly summary, using fallback")
                    .log();
                return buildFallbackSummary(dualValidation);
            }

        } catch (Exception e) {
            log.error("Error generating user-friendly summary, using fallback")
                .exception(e)
                .log();
            return buildFallbackSummary(dualValidation);
        }
    }

    /**
     * Build prompt for LLM to generate user-friendly summary.
     */
    private String buildSummaryPrompt(DualValidationResult dualValidation, String issueTitle) {
        var finalVerdict = dualValidation.finalVerdict();
        var assertionVerdict = dualValidation.assertionBasedVerdict();
        var ruleVerdict = dualValidation.ruleBasedVerdict();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a brief, user-friendly explanation (2-3 sentences max) of this validation result.\n\n");

        prompt.append("Analysis: ").append(issueTitle).append("\n\n");

        prompt.append("Validation Outcome: ").append(finalVerdict.status()).append("\n");
        prompt.append("Overall Confidence: ").append(String.format("%.0f%%", finalVerdict.confidence() * 100)).append("\n\n");

        prompt.append("Evidence Checked:\n");
        prompt.append("- Analyzed ").append(assertionVerdict.totalAssertions()).append(" key claims from the analysis\n");
        prompt.append("- Found ").append(assertionVerdict.supportedAssertions()).append(" supported by evidence, ");
        prompt.append(assertionVerdict.unsupportedAssertions()).append(" contradicted, ");
        prompt.append(assertionVerdict.unknownAssertions()).append(" uncertain\n");

        prompt.append("- Checked ").append(ruleVerdict.getRequiredTotal()).append(" diagnostic rules for \"").append(ruleVerdict.getHypothesis()).append("\"\n");
        prompt.append("- ").append(ruleVerdict.getRequiredPassed()).append(" rules passed, ");
        prompt.append("scored ").append(ruleVerdict.getTotalScore()).append(" points\n\n");

        prompt.append("Write a simple explanation for a non-technical user explaining whether the analysis is correct and why. ");
        prompt.append("Focus on what this means practically. Do not use technical terms like 'assertions', 'rules', 'hypothesis', 'confidence score'. ");
        prompt.append("Use simple language like 'the analysis is correct/incorrect because we found evidence that...'");

        return prompt.toString();
    }

    /**
     * Build simple fallback summary without LLM.
     */
    private String buildFallbackSummary(DualValidationResult dualValidation) {
        var finalVerdict = dualValidation.finalVerdict();
        var assertionVerdict = dualValidation.assertionBasedVerdict();
        var ruleVerdict = dualValidation.ruleBasedVerdict();

        String statusText = switch (finalVerdict.status()) {
            case SUPPORTED -> "The analysis is correct";
            case UNSUPPORTED -> "The analysis is not supported by evidence";
            case PARTIALLY_SUPPORTED -> "The analysis is partially correct";
            case UNKNOWN -> "We cannot confirm the analysis";
        };

        int totalEvidence = assertionVerdict.totalAssertions() + ruleVerdict.getAllResults().size();
        int supportingEvidence = assertionVerdict.supportedAssertions() +
            (int) ruleVerdict.getAllResults().stream().filter(r -> r.isPassed()).count();

        return String.format(
            "%s. We checked %d pieces of evidence and found %d that support this conclusion.",
            statusText, totalEvidence, supportingEvidence
        );
    }
}
