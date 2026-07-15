package com.causa.api.dto.response;

import com.causa.core.domain.validation.DualValidationResult;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.rules.HypothesisValidationResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Validation Detail Response DTO
 *
 * <p>Full validation schema exposed via validation API.
 * Contains detailed assertion and rule validation results.
 *
 * @since 0.0.1
 */
public record ValidationDetailResponse(
    @JsonProperty("diagnostic_id")
    String diagnosticId,

    @JsonProperty("validated_at")
    Instant validatedAt,

    @JsonProperty("final_verdict")
    FinalVerdictDto finalVerdict,

    @JsonProperty("assertion_validation")
    AssertionValidationDto assertionValidation,

    @JsonProperty("rule_validation")
    RuleValidationDto ruleValidation
) {

    /**
     * Creates response from DualValidationResult.
     */
    public static ValidationDetailResponse from(
        String diagnosticId,
        DualValidationResult validationData
    ) {
        return new ValidationDetailResponse(
            diagnosticId,
            Instant.now(),
            validationData.finalVerdict() != null
                ? FinalVerdictDto.from(validationData.finalVerdict())
                : null,
            validationData.assertionBasedVerdict() != null
                ? AssertionValidationDto.from(validationData.assertionBasedVerdict())
                : null,
            validationData.ruleBasedVerdict() != null
                ? RuleValidationDto.from(validationData.ruleBasedVerdict())
                : null
        );
    }

    /**
     * Final verdict DTO.
     */
    public record FinalVerdictDto(
        String status,
        double confidence,
        String strategy,
        String explanation
    ) {
        public static FinalVerdictDto from(DualValidationResult.FinalVerdict verdict) {
            return new FinalVerdictDto(
                verdict.status().name(),
                verdict.confidence(),
                verdict.strategy().name(),
                verdict.explanation()
            );
        }
    }

    /**
     * Assertion-based validation DTO.
     */
    public record AssertionValidationDto(
        String status,
        double confidence,

        @JsonProperty("total_assertions")
        int totalAssertions,

        @JsonProperty("supported_assertions")
        int supportedAssertions,

        @JsonProperty("partially_supported_assertions")
        int partiallySupportedAssertions,

        @JsonProperty("unsupported_assertions")
        int unsupportedAssertions,

        @JsonProperty("unknown_assertions")
        int unknownAssertions,

        String explanation
    ) {
        public static AssertionValidationDto from(DualValidationResult.AssertionBasedVerdict verdict) {
            return new AssertionValidationDto(
                verdict.status().name(),
                verdict.confidence(),
                verdict.totalAssertions(),
                verdict.supportedAssertions(),
                verdict.partiallySupportedAssertions(),
                verdict.unsupportedAssertions(),
                verdict.unknownAssertions(),
                verdict.explanation()
            );
        }
    }

    /**
     * Individual assertion validation result DTO.
     */
    public record AssertionResultDto(
        @JsonProperty("assertion_id")
        String assertionId,

        @JsonProperty("assertion_text")
        String assertionText,

        @JsonProperty("assertion_type")
        String assertionType,

        @JsonProperty("assertion_source")
        String assertionSource,

        String status,
        double confidence,

        @JsonProperty("evidence_count")
        int evidenceCount,

        List<EvidenceDto> evidence
    ) {
        public static AssertionResultDto from(ValidationResult result) {
            return new AssertionResultDto(
                result.assertion().id(),
                result.assertion().text(),
                result.assertion().type().name(),
                result.assertion().source().name(),
                result.status().name(),
                result.confidence(),
                result.supportingEvidence().size(),
                result.supportingEvidence().stream()
                    .map(EvidenceDto::from)
                    .toList()
            );
        }
    }

    /**
     * Evidence DTO.
     */
    public record EvidenceDto(
        String source,
        String type,
        String snippet,

        @JsonProperty("relevance_score")
        double relevanceScore
    ) {
        public static EvidenceDto from(com.causa.core.domain.validation.Evidence evidence) {
            return new EvidenceDto(
                evidence.source(),
                evidence.type().name(),
                evidence.snippet(),
                evidence.relevanceScore()
            );
        }
    }

    /**
     * Rule-based validation DTO.
     */
    public record RuleValidationDto(
        String status,
        double confidence,
        String hypothesis,

        @JsonProperty("total_score")
        int totalScore,

        @JsonProperty("required_passed")
        long requiredPassed,

        @JsonProperty("required_total")
        long requiredTotal,

        @JsonProperty("supporting_matched")
        long supportingMatched,

        @JsonProperty("exclusion_matched")
        long exclusionMatched,

        List<RuleResultDto> results
    ) {
        public static RuleValidationDto from(HypothesisValidationResult result) {
            return new RuleValidationDto(
                result.getStatus().name(),
                result.getConfidence(),
                result.getHypothesis(),
                result.getTotalScore(),
                result.getRequiredPassed(),
                result.getRequiredTotal(),
                result.getSupportingMatched(),
                result.getExclusionMatched(),
                result.getAllResults().stream()
                    .map(RuleResultDto::from)
                    .toList()
            );
        }
    }

    /**
     * Individual rule evaluation result DTO.
     */
    public record RuleResultDto(
        @JsonProperty("rule_id")
        String ruleId,

        @JsonProperty("rule_description")
        String ruleDescription,

        @JsonProperty("rule_type")
        String ruleType,

        boolean passed,
        int weight,
        String message,

        @JsonProperty("matched_signals")
        List<String> matchedSignals
    ) {
        public static RuleResultDto from(com.causa.core.services.rules.RuleEvaluationResult result) {
            return new RuleResultDto(
                result.getRule().getId(),
                result.getRule().getDescription(),
                result.getRule().getType().name(),
                result.isPassed(),
                result.getRule().getWeight(),
                result.getReasoning(),
                result.getMatchedSignals().stream()
                    .map(signal -> signal.getName() + "=" + signal.getValue())
                    .toList()
            );
        }
    }
}
