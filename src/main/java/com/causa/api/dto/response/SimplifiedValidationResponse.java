package com.causa.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified Validation Response for UI
 *
 * <p>User-friendly validation response that hides implementation details
 * (rule weights, internal signals, rule IDs, confidence calculations, etc.)
 *
 * @since 0.0.1
 */
public record SimplifiedValidationResponse(
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
     * Creates simplified response from validation JSON.
     */
    public static SimplifiedValidationResponse from(String validationDataJson, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(validationDataJson);

            Instant validatedAt = parseInstant(root.get("validatedAt"));
            FinalVerdictDto finalVerdict = parseFinalVerdict(root.get("finalVerdict"));
            AssertionValidationDto assertionValidation = parseAssertionValidation(root.get("assertionValidation"));
            RuleValidationDto ruleValidation = parseRuleValidation(root.get("ruleValidation"));

            return new SimplifiedValidationResponse(
                validatedAt,
                finalVerdict,
                assertionValidation,
                ruleValidation
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse validation data", e);
        }
    }

    private static Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return Instant.parse(node.asText());
    }

    private static FinalVerdictDto parseFinalVerdict(JsonNode node) {
        if (node == null || node.isNull()) return null;

        String status = node.has("status") ? node.get("status").asText() : null;
        Double confidence = node.has("confidence") ? node.get("confidence").asDouble() : null;
        String summary = node.has("userFriendlyExplanation")
            ? node.get("userFriendlyExplanation").asText()
            : (node.has("explanation") ? node.get("explanation").asText() : null);

        return new FinalVerdictDto(status, confidence, summary);
    }

    private static AssertionValidationDto parseAssertionValidation(JsonNode node) {
        if (node == null || node.isNull()) return null;

        // Parse summary
        JsonNode summaryNode = node.get("summary");
        AssertionSummaryDto summary = null;
        if (summaryNode != null && !summaryNode.isNull()) {
            summary = new AssertionSummaryDto(
                summaryNode.has("status") ? summaryNode.get("status").asText() : null,
                summaryNode.has("confidence") ? summaryNode.get("confidence").asDouble() : null,
                summaryNode.has("totalAssertions") ? summaryNode.get("totalAssertions").asInt() : 0,
                summaryNode.has("supportedAssertions") ? summaryNode.get("supportedAssertions").asInt() : 0,
                summaryNode.has("partiallySupportedAssertions") ? summaryNode.get("partiallySupportedAssertions").asInt() : 0,
                summaryNode.has("unsupportedAssertions") ? summaryNode.get("unsupportedAssertions").asInt() : 0,
                summaryNode.has("unknownAssertions") ? summaryNode.get("unknownAssertions").asInt() : 0
            );
        }

        // Parse results
        List<AssertionResultDto> results = new ArrayList<>();
        JsonNode resultsNode = node.get("results");
        if (resultsNode != null && resultsNode.isArray()) {
            for (JsonNode resultNode : resultsNode) {
                results.add(parseAssertionResult(resultNode));
            }
        }

        return new AssertionValidationDto(summary, results);
    }

    private static AssertionResultDto parseAssertionResult(JsonNode node) {
        JsonNode assertionNode = node.get("assertion");
        String assertionText = assertionNode != null && assertionNode.has("text")
            ? assertionNode.get("text").asText()
            : null;

        String status = node.has("status") ? node.get("status").asText() : null;
        Double confidence = node.has("confidence") ? node.get("confidence").asDouble() : null;
        String explanation = node.has("explanation") ? node.get("explanation").asText() : null;

        // Parse supporting evidence (simplified)
        List<EvidenceDto> supportingEvidence = new ArrayList<>();
        JsonNode supportingNode = node.get("supportingEvidence");
        if (supportingNode != null && supportingNode.isArray()) {
            for (JsonNode evidenceNode : supportingNode) {
                String source = evidenceNode.has("source") ? evidenceNode.get("source").asText() : "Unknown";
                String snippet = evidenceNode.has("snippet") ? evidenceNode.get("snippet").asText() : "";
                supportingEvidence.add(new EvidenceDto(source, snippet));
            }
        }

        // Parse refuting evidence (simplified)
        List<EvidenceDto> refutingEvidence = new ArrayList<>();
        JsonNode refutingNode = node.get("refutingEvidence");
        if (refutingNode != null && refutingNode.isArray()) {
            for (JsonNode evidenceNode : refutingNode) {
                String source = evidenceNode.has("source") ? evidenceNode.get("source").asText() : "Unknown";
                String snippet = evidenceNode.has("snippet") ? evidenceNode.get("snippet").asText() : "";
                refutingEvidence.add(new EvidenceDto(source, snippet));
            }
        }

        return new AssertionResultDto(assertionText, status, confidence, explanation, supportingEvidence, refutingEvidence);
    }

    private static RuleValidationDto parseRuleValidation(JsonNode node) {
        if (node == null || node.isNull()) return null;

        // Parse summary
        JsonNode summaryNode = node.get("summary");
        RuleSummaryDto summary = null;
        if (summaryNode != null && !summaryNode.isNull()) {
            summary = new RuleSummaryDto(
                summaryNode.has("status") ? summaryNode.get("status").asText() : null,
                summaryNode.has("confidence") ? summaryNode.get("confidence").asDouble() : null,
                summaryNode.has("hypothesis") ? summaryNode.get("hypothesis").asText() : null,
                summaryNode.has("requiredPassed") ? summaryNode.get("requiredPassed").asInt() : 0,
                summaryNode.has("requiredTotal") ? summaryNode.get("requiredTotal").asInt() : 0,
                summaryNode.has("supportingMatched") ? summaryNode.get("supportingMatched").asInt() : 0,
                summaryNode.has("explanation") ? summaryNode.get("explanation").asText() : null
            );
        }

        // Parse results (simplified)
        List<RuleResultDto> results = new ArrayList<>();
        JsonNode resultsNode = node.get("results");
        if (resultsNode != null && resultsNode.isArray()) {
            for (JsonNode resultNode : resultsNode) {
                results.add(parseRuleResult(resultNode));
            }
        }

        return new RuleValidationDto(summary, results);
    }

    private static RuleResultDto parseRuleResult(JsonNode node) {
        JsonNode ruleNode = node.get("rule");
        String ruleDescription = ruleNode != null && ruleNode.has("description")
            ? ruleNode.get("description").asText()
            : (node.has("ruleDescription") ? node.get("ruleDescription").asText() : null);

        String status = node.has("passed") ? (node.get("passed").asBoolean() ? "PASSED" : "FAILED") : null;
        String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : null;

        return new RuleResultDto(ruleDescription, status, reasoning);
    }

    /**
     * Final verdict DTO (simplified).
     */
    public record FinalVerdictDto(
        String status,
        Double confidence,
        String summary
    ) {}

    /**
     * Assertion validation DTO (simplified).
     */
    public record AssertionValidationDto(
        AssertionSummaryDto summary,
        List<AssertionResultDto> results
    ) {}

    /**
     * Assertion summary.
     */
    public record AssertionSummaryDto(
        String status,
        Double confidence,

        @JsonProperty("total_assertions")
        int totalAssertions,

        @JsonProperty("supported_assertions")
        int supportedAssertions,

        @JsonProperty("partially_supported_assertions")
        int partiallySupportedAssertions,

        @JsonProperty("unsupported_assertions")
        int unsupportedAssertions,

        @JsonProperty("unknown_assertions")
        int unknownAssertions
    ) {}

    /**
     * Assertion result (simplified).
     */
    public record AssertionResultDto(
        String assertion,
        String status,
        Double confidence,
        String explanation,

        @JsonProperty("supporting_evidence")
        List<EvidenceDto> supportingEvidence,

        @JsonProperty("refuting_evidence")
        List<EvidenceDto> refutingEvidence
    ) {}

    /**
     * Evidence (simplified - just source and snippet).
     */
    public record EvidenceDto(
        String source,
        String snippet
    ) {}

    /**
     * Rule validation DTO (simplified).
     */
    public record RuleValidationDto(
        RuleSummaryDto summary,
        List<RuleResultDto> results
    ) {}

    /**
     * Rule summary.
     */
    public record RuleSummaryDto(
        String status,
        Double confidence,
        String hypothesis,

        @JsonProperty("required_rules_passed")
        int requiredRulesPassed,

        @JsonProperty("required_rules_total")
        int requiredRulesTotal,

        @JsonProperty("supporting_rules_matched")
        int supportingRulesMatched,

        String summary
    ) {}

    /**
     * Rule result (simplified - no weights, signals, or IDs).
     */
    public record RuleResultDto(
        String rule,
        String status,
        String reasoning
    ) {}
}
