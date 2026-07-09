package com.causa.core.services.diagnostic.impl;

import com.causa.common.constants.DiagnosticConstants;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.LogFields;
import com.causa.common.constants.JsonParsingConstants;
import com.causa.common.constants.ContextConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LLMConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.ValidatedRCA;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.diagnostic.DiagnosticService;
import com.causa.core.services.diagnostic.RcaPromptBuilder;
import com.causa.core.services.validation.RcaValidator;
import com.causa.mcp.McpContextCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import com.causa.core.domain.DiagnosticContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Diagnostic Service Implementation
 *
 * <p>Implements the diagnostic pipeline with placeholder methods for future LLM integration.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticServiceImpl implements DiagnosticService {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticServiceImpl.class);

    private final DiagnosticRepository diagnosticRepository;
    private final McpContextCollector mcpContextCollector;
    private final RcaPromptBuilder rcaPromptBuilder;
    private final PromptSender promptSender;
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Optional<RcaValidator> rcaValidator;

    @Inject
    public DiagnosticServiceImpl(DiagnosticRepository diagnosticRepository,
                                  McpContextCollector mcpContextCollector,
                                  RcaPromptBuilder rcaPromptBuilder,
                                  PromptSender promptSender,
                                  LLMConfig llmConfig,
                                  ObjectMapper objectMapper,
                                  Validator validator,
                                  Instance<RcaValidator> rcaValidatorInstance) {
        this.diagnosticRepository = diagnosticRepository;
        this.mcpContextCollector = mcpContextCollector;
        this.rcaPromptBuilder = rcaPromptBuilder;
        this.promptSender = promptSender;
        this.llmConfig = llmConfig;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.rcaValidator = rcaValidatorInstance.isResolvable() ?
            Optional.of(rcaValidatorInstance.get()) : Optional.empty();
    }

    @Override
    public Diagnostic triggerDiagnostics(Alert alert) {
        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field("alertName", alert.getAlertName())
            .log();

        // Generate diagnostic ID
        Instant now = Instant.now();
        String diagnosticId = Diagnostic.generateDiagnosticId(alert.getAlertId(), now);

        // Create diagnostic in PENDING status
        Diagnostic diagnostic = Diagnostic.builder()
            .diagnosticId(diagnosticId)
            .alertId(alert.getAlertId())
            .status(DiagnosticStatus.PENDING)
            .generatedAt(now)
            .build();

        // Persist diagnostic
        diagnostic = diagnosticRepository.save(diagnostic);

        log.info("Diagnostic created")
            .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field(LogFields.STATUS, DiagnosticStatus.PENDING.getValue())
            .log();

        // TODO: Trigger async diagnostic pipeline
        // For now, call diagnostic pipeline synchronously

        // Step 1: Collect diagnostic context from MCP servers (K8s, Kruize, Cryostat)
        // ==============================================
        // HARDCODED FOR TESTING - Using string context directly
        // ==============================================
        String contextForLLM = mcpContextCollector.collectContextAsString(alert);

        log.info("TESTING: Using hardcoded MCP context string directly")
            .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field("contextLength", contextForLLM.length())
            .log();
        // ==============================================
        // END HARDCODED - FOR PRODUCTION USE:
        // DiagnosticContext diagnosticContext = collectContext(alert);
        // String contextForLLM = diagnosticContext.toString();
        // ==============================================

        // Step 2: (Already have string context above)
        String separator = ContextConstants.SEPARATOR_CHAR.repeat(ContextConstants.SEPARATOR_LENGTH);

        // Log the full formatted context that will be sent to LLM
        log.info(ContextConstants.NEWLINE + separator + ContextConstants.NEWLINE +
                 ContextConstants.CONTEXT_LOG_HEADER + ContextConstants.NEWLINE +
                 separator + ContextConstants.NEWLINE +
                 contextForLLM +
                 separator + ContextConstants.NEWLINE)
            .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
            .log();

        // Step 3: Perform root cause analysis using LLM
        RootCauseAnalysis rca = performRootCauseAnalysis(alert, contextForLLM);

        // Step 4: Validate RCA against collected context
        ValidatedRCA validatedRCA = validateRca(alert, rca, contextForLLM);

        // Step 5: Update diagnostic with RCA and validation results
        diagnostic = updateDiagnosticWithValidation(diagnostic, rca, validatedRCA);

        return diagnostic;
    }

    /**
     * Collects diagnostic context from all MCP servers (Kubernetes, Cryostat, Kruize).
     *
     * <p>Aggregates pod status, events, logs, resource recommendations, and JFR analysis
     * from multiple MCP servers into a single {@link com.causa.core.domain.DiagnosticContext} object.
     *
     * @param alert the alert to collect context for
     * @return diagnostic context with all collected data
     */
    private DiagnosticContext collectContext(Alert alert) {
        log.debug(LogMessages.Diagnostic.CONTEXT_COLLECTION_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        return mcpContextCollector.collectContext(alert);
    }

    /**
     * Performs root cause analysis using LLM.
     *
     * <p>Builds the RCA prompt from YAML template, calls the LLM, and parses
     * the structured JSON response into a RootCauseAnalysis object.
     *
     * @param alert the alert to analyze
     * @param contextString the collected MCP context
     * @return the RCA result
     */
    private RootCauseAnalysis performRootCauseAnalysis(Alert alert, String contextString) {
        log.debug(LogMessages.Diagnostic.ROOT_CAUSE_ANALYSIS_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        try {
            // ==============================================
            // HARDCODED FOR TESTING - REMOVE IN PRODUCTION
            // ==============================================
            log.warn("TESTING: Using hardcoded heap OOM RCA instead of calling LLM")
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .log();

            RootCauseAnalysis rca = createHardcodedHeapOomRca();
            // ==============================================
            // END HARDCODED TEST DATA - FOR PRODUCTION, UNCOMMENT THE CODE BELOW
            // ==============================================

            /*
            // Build the prompt using YAML template
            String systemPrompt = rcaPromptBuilder.getSystemPrompt();
            String userPrompt = rcaPromptBuilder.buildPrompt(alert, contextString);

            log.info(LogMessages.Diagnostic.RCA_PROMPT_BUILT)
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .field(LogFields.SYSTEM_PROMPT_LENGTH, systemPrompt.length())
                .field(LogFields.USER_PROMPT_LENGTH, userPrompt.length())
                .log();

            log.debug("Context and prompts prepared")
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .field(LogFields.CONTEXT_LENGTH, contextString.length())
                .field(LogFields.SYSTEM_PROMPT_LENGTH, systemPrompt.length())
                .field(LogFields.USER_PROMPT_LENGTH, userPrompt.length())
                .log();

            // Build LLM request
            LLMRequest llmRequest = LLMRequest.builder(userPrompt)
                .systemPrompt(systemPrompt)
                .temperature(llmConfig.temperature())
                .maxTokens(llmConfig.maxTokens())
                .build();

            // Call the LLM (works with both LangChain and BobShell)
            LLMResponse llmResponse = promptSender.send(llmRequest);

            log.info(LogMessages.Diagnostic.LLM_RESPONSE_RECEIVED)
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .field("modelUsed", llmResponse.modelUsed())
                .field("inputTokens", llmResponse.inputTokens())
                .field("outputTokens", llmResponse.outputTokens())
                .field("latencyMs", llmResponse.latencyMs())
                .log();

            // Parse JSON response to RootCauseAnalysis
            String responseText = llmResponse.responseText();

            log.debug("Parsing LLM response")
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .field("responseLength", responseText.length())
                .log();

            RootCauseAnalysis rca = parseRcaResponse(responseText);
            */

            String rcaSeparator = "=".repeat(80);
            log.info("\n" + rcaSeparator + "\n" +
                     "📊 RCA GENERATED SUCCESSFULLY\n" +
                     rcaSeparator + "\n" +
                     "Issue Title: " + rca.issueTitle() + "\n" +
                     "Issue Description: " + rca.issueDescription() + "\n" +
                     "Anomaly Type: " + rca.anomalyType() + "\n" +
                     "Root Cause: " + rca.rootCause() + "\n" +
                     "LLM Confidence (RCA): " + rca.llmConfidenceScoreForRca() + "\n" +
                     "LLM Confidence (Solution): " + rca.llmConfidenceScoreForSolution() + "\n" +
                     "Solutions Count: " + rca.possibleSolutions().size() + "\n" +
                     "Confidence Summary: " + rca.confidenceSummary() + "\n" +
                     rcaSeparator)
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .log();

            return rca;

        } catch (Exception e) {
            log.error(LogMessages.Diagnostic.RCA_GENERATION_FAILED)
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .exception(e)
                .log();
            throw new RuntimeException("Failed to generate RCA for alert: " + alert.getAlertId(), e);
        }
    }

    /**
     * Parses the LLM JSON response into a RootCauseAnalysis object.
     *
     * <p>Handles markdown code blocks case-insensitively (```json, ```JSON, ```json5, etc.)
     * by removing entire first line if it starts with backticks.
     *
     * @param responseText the LLM response text (should be JSON)
     * @return the parsed RCA
     */
    private RootCauseAnalysis parseRcaResponse(String responseText) throws Exception {
        // Clean the response - remove markdown code blocks if present
        String jsonText = responseText.trim();

        // Handle opening code block case-insensitively
        if (jsonText.startsWith(JsonParsingConstants.CODE_BLOCK_PREFIX)) {
            // Remove entire first line (handles ```json, ```JSON, ```json5, etc.)
            int firstNewline = jsonText.indexOf('\n');
            if (firstNewline > 0) {
                jsonText = jsonText.substring(firstNewline + 1);
            }
        }

        // Handle closing code block
        if (jsonText.endsWith(JsonParsingConstants.CODE_BLOCK_PREFIX)) {
            jsonText = jsonText.substring(0, jsonText.length() - JsonParsingConstants.CODE_BLOCK_PREFIX_LENGTH);
        }

        jsonText = jsonText.trim();

        // Parse JSON to RootCauseAnalysis
        RootCauseAnalysis rca = objectMapper.readValue(jsonText, RootCauseAnalysis.class);

        // Validate the deserialized object
        // Note: Jackson deserialization does NOT trigger Bean Validation annotations automatically
        Set<ConstraintViolation<RootCauseAnalysis>> violations = validator.validate(rca);
        if (!violations.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("RCA validation failed:");
            for (ConstraintViolation<RootCauseAnalysis> violation : violations) {
                errorMsg.append("\n  - ").append(violation.getPropertyPath())
                        .append(": ").append(violation.getMessage());
            }
            throw new IllegalArgumentException(errorMsg.toString());
        }

        return rca;
    }

    /**
     * Validates RCA output against collected diagnostic context.
     *
     * <p>Uses assertion-driven validation to verify each claim in the RCA
     * against the collected diagnostic context. Validates:
     * <ul>
     *   <li>Observations and facts against K8s events and metrics</li>
     *   <li>Trends against time-series data</li>
     *   <li>Causal relationships against evidence chains</li>
     *   <li>Configuration claims against actual settings</li>
     * </ul>
     *
     * @param alert the alert being analyzed
     * @param rca the root cause analysis to validate
     * @param diagnosticContext the collected MCP context
     * @return validated RCA with assertion-level validation results
     */
    private ValidatedRCA validateRca(Alert alert, RootCauseAnalysis rca, String diagnosticContext) {
        log.info(LogMessages.Diagnostic.RCA_VALIDATION_STARTED)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field("issueTitle", rca.issueTitle())
            .log();

        // Check if validator is available
        if (rcaValidator.isEmpty()) {
            log.warn("RCA validator not available, skipping validation")
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .log();

            // Return unvalidated RCA wrapped in ValidatedRCA with no validation results
            return ValidatedRCA.builder()
                .originalRca(rca)
                .validationResults(java.util.List.of())
                .validatedAt(Instant.now())
                .build();
        }

        // Perform validation
        ValidatedRCA validatedRCA = rcaValidator.get().validate(rca, diagnosticContext);

        // Log validation results
        logValidationResults(validatedRCA);

        // Log validation summary
        log.info("RCA validation completed")
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field("validationSummary", validatedRCA.summary().toSummaryString())
            .field("isValid", validatedRCA.isValid())
            .field("isHighConfidence", validatedRCA.isHighConfidence())
            .field(LogFields.SUPPORTED_COUNT, validatedRCA.getSupportedAssertions().size())
            .field(LogFields.UNSUPPORTED_COUNT, validatedRCA.getUnsupportedAssertions().size())
            .field(LogFields.UNKNOWN_COUNT, validatedRCA.getUnknownAssertions().size())
            .log();

        return validatedRCA;
    }

    /**
     * Logs detailed validation results for each assertion.
     */
    private void logValidationResults(ValidatedRCA validatedRCA) {
        log.info("\n" + "=".repeat(80))
            .log();
        log.info("RCA VALIDATION RESULTS")
            .log();
        log.info("=".repeat(80))
            .log();

        // Log each assertion's validation result
        for (ValidationResult result : validatedRCA.validationResults()) {
            String statusSymbol = switch (result.status()) {
                case SUPPORTED -> "✓";
                case PARTIALLY_SUPPORTED -> "~";
                case UNSUPPORTED -> "✗";
                case UNKNOWN -> "?";
            };

            log.info(String.format("[%s] %s", statusSymbol, result.assertion().text()))
                .field("assertionId", result.assertion().id())
                .field("type", result.assertion().type())
                .field("source", result.assertion().source())
                .field("status", result.status())
                .field("confidence", String.format("%.2f", result.confidence()))
                .field("supportingEvidence", result.supportingEvidence().size())
                .field("refutingEvidence", result.refutingEvidence().size())
                .log();

            // Log evidence details if present
            if (!result.supportingEvidence().isEmpty()) {
                for (int i = 0; i < result.supportingEvidence().size(); i++) {
                    var evidence = result.supportingEvidence().get(i);
                    log.debug(String.format("  Evidence %d: %s (relevance: %.2f)",
                        i + 1,
                        evidence.snippet().substring(0, Math.min(100, evidence.snippet().length())),
                        evidence.relevanceScore()))
                        .field("evidenceSource", evidence.source())
                        .field("evidenceType", evidence.type())
                        .log();
                }
            }

            // Log explanation
            result.explanation().ifPresent(explanation ->
                log.debug("  Explanation: " + explanation)
                    .log()
            );
        }

        log.info("=".repeat(80))
            .log();
        log.info("VALIDATION SUMMARY")
            .log();
        log.info(validatedRCA.summary().toSummaryString())
            .log();
        log.info("=".repeat(80))
            .log();
    }

    /**
     * Updates diagnostic with RCA and validation results.
     *
     * @param diagnostic the diagnostic to update
     * @param rca the root cause analysis
     * @param validatedRCA the validated RCA with validation results
     * @return updated diagnostic
     */
    private Diagnostic updateDiagnosticWithValidation(
        Diagnostic diagnostic,
        RootCauseAnalysis rca,
        ValidatedRCA validatedRCA
    ) {
        log.info("Starting to build validation persistence data")
            .field(LogFields.DIAGNOSTIC_ID, diagnostic.getDiagnosticId())
            .log();

        try {
            // Convert RCA to JSON string
            String rcaJsonString = objectMapper.writeValueAsString(rca);

            // Determine overall validation result
            String validationResult = determineValidationResult(validatedRCA);

            log.info("Validation result determined")
                .field("validationResult", validationResult)
                .log();

            // Build validation data JSON
            com.fasterxml.jackson.databind.node.ObjectNode validationDataNode = objectMapper.createObjectNode();

            // Add dual validation if available
            if (validatedRCA.dualValidation() != null) {
                validationDataNode.set("dualValidation", objectMapper.valueToTree(validatedRCA.dualValidation()));
            }

            // Add summary
            validationDataNode.set("summary", objectMapper.valueToTree(validatedRCA.summary()));

            // Add all validation results
            validationDataNode.set("validationResults", objectMapper.valueToTree(validatedRCA.validationResults()));

            // Add validated timestamp
            validationDataNode.put("validatedAt", validatedRCA.validatedAt().toString());

            // Convert validation data to JSON string (compact for DB)
            String validationDataString = objectMapper.writeValueAsString(validationDataNode);

            // Create pretty-printed JSON for logging
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationDataNode);

            // Log assertions summary
            log.info("\n" + "=".repeat(80) + "\n" +
                     "📝 ASSERTIONS VALIDATED (" + validatedRCA.validationResults().size() + " total)\n" +
                     "=".repeat(80))
                .log();

            for (int i = 0; i < validatedRCA.validationResults().size(); i++) {
                var result = validatedRCA.validationResults().get(i);
                String statusIcon = switch(result.status()) {
                    case SUPPORTED -> "✅";
                    case PARTIALLY_SUPPORTED -> "🟡";
                    case UNSUPPORTED -> "❌";
                    case UNKNOWN -> "❓";
                };
                log.info(String.format("  [%d] %s %s: %s (conf=%.2f, evidence=%d supporting)",
                        i+1, statusIcon, result.assertion().type(),
                        result.assertion().text(), result.confidence(),
                        result.supportingEvidence().size()))
                    .log();
            }

            // Log rules summary if available
            if (validatedRCA.dualValidation() != null && validatedRCA.dualValidation().ruleBasedVerdict() != null) {
                var ruleVerdict = validatedRCA.dualValidation().ruleBasedVerdict();
                log.info("\n" + "=".repeat(80) + "\n" +
                         "📋 RULES EVALUATED (Hypothesis: " + ruleVerdict.getHypothesis() + ")\n" +
                         "=".repeat(80))
                    .log();

                log.info(String.format("  Required Rules: %d/%d passed",
                        ruleVerdict.getRequiredPassed(), ruleVerdict.getRequiredTotal()))
                    .log();
                log.info(String.format("  Supporting Rules: %d matched", ruleVerdict.getSupportingMatched()))
                    .log();
                log.info(String.format("  Exclusion Rules: %d matched", ruleVerdict.getExclusionMatched()))
                    .log();
                log.info(String.format("  Total Score: %d/%d (%.1f%%) | Confidence: %.2f",
                        ruleVerdict.getTotalScore(),
                        ruleVerdict.getMaxPossibleScore(),
                        ruleVerdict.getNormalizedScore() * 100,
                        ruleVerdict.getConfidence()))
                    .log();

                var breakdown = ruleVerdict.getScoreBreakdown();
                log.info(String.format("  Score Breakdown: Required=%d, Supporting=%d, Exclusion=%d",
                        breakdown.getRequiredScore(),
                        breakdown.getSupportingScore(),
                        breakdown.getExclusionScore()))
                    .log();
            }

            // Update diagnostic
            Diagnostic updated = Diagnostic.builder()
                .diagnosticId(diagnostic.getDiagnosticId())
                .alertId(diagnostic.getAlertId())
                .status(DiagnosticStatus.COMPLETED)
                .generatedAt(diagnostic.getGeneratedAt())
                .confidenceScore((float) validatedRCA.summary().averageConfidence())
                .faultDomain(null)  // Let other dev handle fault domain mapping from RCA
                .rootCauseAnalysis(rcaJsonString)
                .validationResult(validationResult)
                .validationData(validationDataString)
                .build();

            // Log validation persistence data before saving
            log.info("\n" + "=".repeat(80) + "\n" +
                     "💾 VALIDATION PERSISTENCE DATA\n" +
                     "=".repeat(80) + "\n" +
                     "validation_result: " + validationResult + "\n" +
                     "validation_data (JSONB):\n" +
                     prettyJson + "\n" +
                     "=".repeat(80))
                .log();

            // Persist to database
            updated = diagnosticRepository.save(updated);

            log.info("Diagnostic updated with validation results")
                .field(LogFields.DIAGNOSTIC_ID, diagnostic.getDiagnosticId())
                .field(LogFields.VALIDATION_RESULT, validationResult)
                .field(LogFields.CONFIDENCE_SCORE, validatedRCA.summary().averageConfidence())
                .log();

            return updated;

        } catch (Exception e) {
            // Try to log validation data even on failure
            try {
                String validationResult = determineValidationResult(validatedRCA);
                com.fasterxml.jackson.databind.node.ObjectNode validationDataNode = objectMapper.createObjectNode();
                if (validatedRCA.dualValidation() != null) {
                    validationDataNode.set("dualValidation", objectMapper.valueToTree(validatedRCA.dualValidation()));
                }
                validationDataNode.set("summary", objectMapper.valueToTree(validatedRCA.summary()));
                validationDataNode.set("validationResults", objectMapper.valueToTree(validatedRCA.validationResults()));
                validationDataNode.put("validatedAt", validatedRCA.validatedAt().toString());
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationDataNode);

                // Log assertions summary
                log.error("\n" + "=".repeat(80) + "\n" +
                         "📝 ASSERTIONS VALIDATED (" + validatedRCA.validationResults().size() + " total) - FAILED TO SAVE\n" +
                         "=".repeat(80))
                    .log();

                for (int i = 0; i < validatedRCA.validationResults().size(); i++) {
                    var result = validatedRCA.validationResults().get(i);
                    String statusIcon = switch(result.status()) {
                        case SUPPORTED -> "✅";
                        case PARTIALLY_SUPPORTED -> "🟡";
                        case UNSUPPORTED -> "❌";
                        case UNKNOWN -> "❓";
                    };
                    log.error(String.format("  [%d] %s %s: %s (conf=%.2f, evidence=%d supporting)",
                            i+1, statusIcon, result.assertion().type(),
                            result.assertion().text(), result.confidence(),
                            result.supportingEvidence().size()))
                        .log();
                }

                // Log rules summary if available
                if (validatedRCA.dualValidation() != null && validatedRCA.dualValidation().ruleBasedVerdict() != null) {
                    var ruleVerdict = validatedRCA.dualValidation().ruleBasedVerdict();
                    log.error("\n" + "=".repeat(80) + "\n" +
                             "📋 RULES EVALUATED (Hypothesis: " + ruleVerdict.getHypothesis() + ") - FAILED TO SAVE\n" +
                             "=".repeat(80))
                        .log();

                    log.error(String.format("  Required Rules: %d/%d passed",
                            ruleVerdict.getRequiredPassed(), ruleVerdict.getRequiredTotal()))
                        .log();
                    log.error(String.format("  Supporting Rules: %d matched", ruleVerdict.getSupportingMatched()))
                        .log();
                    log.error(String.format("  Exclusion Rules: %d matched", ruleVerdict.getExclusionMatched()))
                        .log();
                    log.error(String.format("  Total Score: %d/%d (%.1f%%) | Confidence: %.2f",
                            ruleVerdict.getTotalScore(),
                            ruleVerdict.getMaxPossibleScore(),
                            ruleVerdict.getNormalizedScore() * 100,
                            ruleVerdict.getConfidence()))
                        .log();

                    var breakdown = ruleVerdict.getScoreBreakdown();
                    log.error(String.format("  Score Breakdown: Required=%d, Supporting=%d, Exclusion=%d",
                            breakdown.getRequiredScore(),
                            breakdown.getSupportingScore(),
                            breakdown.getExclusionScore()))
                        .log();
                }

                log.error("\n" + "=".repeat(80) + "\n" +
                         "💾 VALIDATION PERSISTENCE DATA (FAILED TO SAVE)\n" +
                         "=".repeat(80) + "\n" +
                         "validation_result: " + validationResult + "\n" +
                         "validation_data (JSONB):\n" +
                         prettyJson + "\n" +
                         "=".repeat(80))
                    .log();
            } catch (Exception jsonEx) {
                // Ignore JSON build errors in error handler
            }

            log.error("Failed to update diagnostic with validation results")
                .field(LogFields.DIAGNOSTIC_ID, diagnostic.getDiagnosticId())
                .exception(e)
                .log();

            // Return original diagnostic with FAILED status
            return Diagnostic.builder()
                .diagnosticId(diagnostic.getDiagnosticId())
                .alertId(diagnostic.getAlertId())
                .status(DiagnosticStatus.FAILED)
                .generatedAt(diagnostic.getGeneratedAt())
                .build();
        }
    }

    /**
     * Determines the overall validation result string from ValidatedRCA.
     *
     * @param validatedRCA the validated RCA
     * @return validation result string (SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED)
     */
    private String determineValidationResult(ValidatedRCA validatedRCA) {
        // If dual validation available, use final verdict
        if (validatedRCA.dualValidation() != null) {
            return validatedRCA.dualValidation().finalVerdict().status().name();
        }

        // Otherwise use assertion-based summary
        if (validatedRCA.isHighConfidence()) {
            return "SUPPORTED";
        } else if (validatedRCA.isValid()) {
            return "PARTIALLY_SUPPORTED";
        } else {
            return "UNSUPPORTED";
        }
    }

    /**
     * Creates a hardcoded heap OOM RCA for testing the validation pipeline.
     * REMOVE IN PRODUCTION.
     */
    private RootCauseAnalysis createHardcodedHeapOomRca() {
        var supportingLogs = java.util.List.of(
            "2026-06-18 06:09:01,405 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 159000 targets. Current registry size=159000",
            "2026-06-18 06:09:04,839 INFO  [ai.causa.scheduler.ScrapeScheduler] (executor-thread-11) Scrape started. targets=166324",
            "2026-06-18 06:09:04,839 INFO  [ai.causa.scheduler.ScrapeScheduler] (executor-thread-12) Writer started. targets=166350",
            "Aborting due to java.lang.OutOfMemoryError: Java heap space",
            "#  fatal error: OutOfMemory encountered: Java heap space"
        );

        var evidences = java.util.List.of(
            "Memory usage at 93% capacity: 478/512 MiB (PROMETHEUS_METRICS)",
            "CPU usage: 0.421/0.500 cores, indicating active processing (PROMETHEUS_METRICS)",
            "Application aborted with 'java.lang.OutOfMemoryError: Java heap space' (APPLICATION_LOGS line 12)",
            "Fatal JVM error: 'OutOfMemory encountered: Java heap space' (APPLICATION_LOGS lines 14-17)",
            "Pod entered BackOff state at 06:09:13 UTC after container failure (POD_EVENTS line 7)",
            "JFR shows Serial GC configuration: youngCollector='DefNew', oldCollector='SerialOld', parallelGCThreads=0 (JFR_DATA)",
            "GC pause times increased significantly: 82.4ms → 313ms → 597ms, indicating memory pressure (JFR_DATA)",
            "Large object allocation detected: 99.3 MB byte array allocation (JFR_DATA line 62)",
            "Registry grew from 159,000 to 166,350 targets in ~3 minutes (APPLICATION_LOGS lines 2-11)",
            "Kruize recommends memory increase from 512 MiB to 806 MiB (+294 MiB / +57%) (KRUIZE_RECOMMENDATIONS)",
            "Kruize recommends memory requests increase from 256 MiB to 806 MiB (+550 MiB) (KRUIZE_RECOMMENDATIONS)"
        );

        var solutions = java.util.List.of(
            new RootCauseAnalysis.Solution(
                "Increase memory limits to 806 MiB as recommended by Kruize",
                "Kruize performance analysis shows actual memory requirements exceed current 512 MiB limit",
                RootCauseAnalysis.Solution.SuccessProbability.HIGH,
                "Update deployment yaml: memory.limits=806Mi, memory.requests=806Mi"
            ),
            new RootCauseAnalysis.Solution(
                "Implement bounded data structures with maximum size limits",
                "Registry grew unbounded from 159K to 166K+ targets causing OOM",
                RootCauseAnalysis.Solution.SuccessProbability.HIGH,
                "Use LRU cache or circular buffer with max capacity limit"
            ),
            new RootCauseAnalysis.Solution(
                "Switch from Serial GC to G1GC for multi-core systems",
                "Serial GC pause times increased to 597ms indicating inefficiency on multi-threaded workload",
                RootCauseAnalysis.Solution.SuccessProbability.MEDIUM,
                "Add JVM flag: -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
            ),
            new RootCauseAnalysis.Solution(
                "Implement pagination for target processing",
                "Loading all 166K targets into memory simultaneously exhausts heap",
                RootCauseAnalysis.Solution.SuccessProbability.MEDIUM,
                "Process targets in batches of 10K with streaming API"
            ),
            new RootCauseAnalysis.Solution(
                "Add memory circuit breakers",
                "No protection against memory pressure before OOM occurs",
                RootCauseAnalysis.Solution.SuccessProbability.MEDIUM,
                "Reject new targets when heap usage exceeds 80%, add metrics alerting"
            )
        );

        return new RootCauseAnalysis(
            "Java application OOM crash due to unbounded target registry growth with Serial GC",
            "The pod heap-oom-prom-8554b846d7-v5hj2 crashed due to an out-of-memory condition. The application was continuously inserting targets into a registry, growing from 159,000 to 166,350 targets before running out of heap space. Memory usage reached 93% (478/512 MiB) at the time of alert, and the container entered a BackOff restart loop after the crash.",
            "The Java application experienced a fatal OutOfMemoryError in the heap space while executing the DiscoveryScheduler and ScrapeScheduler components. The application logs show continuous target insertion operations, with the registry size growing from 159,000 to 166,350 targets within approximately 3 minutes (06:09:01 to 06:09:04). At 06:09:04, the application aborted with 'java.lang.OutOfMemoryError: Java heap space'. JFR profiling data reveals the application was using Serial GC (DefNew for young generation, SerialOld for old generation) with parallelGCThreads=0, which is suboptimal for multi-threaded workloads. Multiple GC events show increasing pause times (82.4ms, 313ms, 597ms), indicating memory pressure. Large object allocations were detected, including a 99.3 MB byte array allocation. Prometheus metrics confirm memory usage at 93% of the 512 MiB limit. The pod entered BackOff state at 06:09:13, indicating Kubernetes attempted to restart the failed container.",
            AnomalyType.POSSIBLE_OOM_KILLED,
            "The root cause is unbounded memory growth due to continuous insertion of targets into an in-memory registry without proper size limits or memory management. The application loaded 166,350 targets into memory, causing heap exhaustion. This was exacerbated by the use of Serial GC (single-threaded garbage collector) on what appears to be a multi-core system, leading to inefficient memory reclamation with long GC pause times (up to 597ms). The combination of rapid data accumulation, inadequate heap size (512 MiB limit), and inefficient GC configuration created a memory pressure scenario that culminated in OutOfMemoryError. The application's memory limit was insufficient for the workload, as evidenced by Kruize recommendations suggesting an increase to 806 MiB (+294 MiB).",
            supportingLogs,
            evidences,
            solutions,
            0.95,
            0.90,
            "HIGH confidence based on strong evidence from application logs showing explicit OOM errors, memory metrics at 93% utilization, and JFR data showing memory pressure through increasing GC pause times",
            "All five signal types were available for analysis: PROMETHEUS_METRICS, APPLICATION_LOGS, POD_EVENTS, JFR_DATA, and KRUIZE_RECOMMENDATIONS. Anomaly categorized as POSSIBLE_OOM_KILLED rather than OOM_KILLED because while the application logs clearly show OutOfMemoryError and fatal JVM error, the POD_EVENTS do not contain an explicit 'OOMKilled' event or exit code 137. The BackOff event indicates container restart attempts but doesn't specify OOMKilled as the reason. Confidence level is HIGH (95%) based on strong evidence. The Serial GC configuration is a contributing factor but not the primary root cause; the unbounded data growth is the main issue."
        );
    }
}
