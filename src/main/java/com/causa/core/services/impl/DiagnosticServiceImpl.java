package com.causa.core.services.impl;

import com.causa.common.constants.DiagnosticConstants;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.Fields;
import com.causa.common.constants.DiagnosticConstants.LogFields;
import com.causa.common.constants.JsonParsingConstants;
import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.ValidationConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.common.utils.IdUtils;
import com.causa.config.AppConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.DiagnosticContext;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.ValidatedRCA;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.DiagnosticService;
import com.causa.core.services.RcaPromptBuilder;
import com.causa.core.services.validation.RcaValidator;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import com.causa.mcp.McpContextCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

/**
 * Diagnostic Service Implementation
 *
 * <p>Implements the diagnostic pipeline with an async execution model:
 * <ol>
 *   <li>{@link #triggerDiagnostics} saves alert (PROCESSING) + diagnostic (PENDING) and returns immediately.</li>
 *   <li>The full MCP context collection + LLM pipeline runs on a background thread via {@link ExecutorService}.</li>
 *   <li>On success → diagnostic COMPLETED, alert PROCESSED.</li>
 *   <li>On failure → diagnostic FAILED, alert PROCESSED.</li>
 * </ol>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticServiceImpl implements DiagnosticService {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticServiceImpl.class);

    private final DiagnosticRepository diagnosticRepository;
    private final AlertRepository alertRepository;
    private final McpContextCollector mcpContextCollector;
    private final RcaPromptBuilder rcaPromptBuilder;
    private final PromptSender promptSender;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ExecutorService pipelineExecutor;
    private final Optional<RcaValidator> rcaValidator;

    @Inject
    public DiagnosticServiceImpl(DiagnosticRepository diagnosticRepository,
                                  AlertRepository alertRepository,
                                  McpContextCollector mcpContextCollector,
                                  RcaPromptBuilder rcaPromptBuilder,
                                  PromptSender promptSender,
                                  AppConfig appConfig,
                                  ObjectMapper objectMapper,
                                  Validator validator,
                                  Instance<RcaValidator> rcaValidatorInstance) {
        this.diagnosticRepository = diagnosticRepository;
        this.alertRepository      = alertRepository;
        this.mcpContextCollector  = mcpContextCollector;
        this.rcaPromptBuilder     = rcaPromptBuilder;
        this.promptSender         = promptSender;
        this.appConfig            = appConfig;
        this.objectMapper         = objectMapper;
        this.validator            = validator;
        this.pipelineExecutor     = Executors.newCachedThreadPool();
        this.rcaValidator         = rcaValidatorInstance.isResolvable() ?
            Optional.of(rcaValidatorInstance.get()) : Optional.empty();
    }

    @Override
    public Diagnostic triggerDiagnostics(Alert alert) {
        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED)
            .field("alertId", alert.getAlertId())
            .field("alertName", alert.getAlertName())
            .log();

        // Generate diagnostic ID in diag_<16> format (VARCHAR(21) in DB)
        Instant now = Instant.now();
        String diagnosticId = IdUtils.generateDiagnosticId();

        // Persist diagnostic with PENDING status immediately — this is returned to the caller
        // before the LLM pipeline even starts.
        Diagnostic diagnostic = Diagnostic.builder()
            .diagnosticId(diagnosticId)
            .alertId(alert.getAlertId())
            .status(DiagnosticStatus.PENDING)
            .generatedAt(now)
            .build();

        diagnostic = diagnosticRepository.save(diagnostic);

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_INITIATED)
            .field("diagnosticId", diagnosticId)
            .field("alertId", alert.getAlertId())
            .field("status", DiagnosticStatus.PENDING.getValue())
            .log();

        // Fire-and-forget: dispatch the full MCP + LLM pipeline to a background thread.
        // The HTTP response is returned immediately — the caller is NOT blocked.
        // Capture finals for the lambda.
        final Diagnostic pendingDiagnostic = diagnostic;
        final Alert      capturedAlert     = alert;
        pipelineExecutor.submit(() -> runPipelineAsync(capturedAlert, pendingDiagnostic));

        return diagnostic;
    }

    /**
     * Runs the full MCP context collection + LLM pipeline on a background thread.
     *
     * <p>Called exclusively by {@link #triggerDiagnostics} via {@link ExecutorService} —
     * never on the HTTP request thread.
     *
     * <p>Status lifecycle managed here:
     * <ol>
     *   <li>PENDING  — set by {@link #triggerDiagnostics} before this method is called</li>
     *   <li>IN_PROGRESS — set at the start of this method, before any MCP/LLM work</li>
     *   <li>COMPLETED or FAILED — set on finish</li>
     * </ol>
     *
     * <p>On success → diagnostic COMPLETED, alert PROCESSED.<br>
     * On failure → diagnostic FAILED, alert PROCESSED.
     *
     * @param alert   the accepted alert being analyzed
     * @param pending the PENDING diagnostic row already saved to the DB
     */
    private void runPipelineAsync(Alert alert, Diagnostic pending) {
        String diagnosticId = pending.getDiagnosticId();

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_PIPELINE_START)
            .field("diagnosticId", diagnosticId)
            .field("alertId", alert.getAlertId())
            .log();

        // Mark diagnostic IN_PROGRESS immediately so callers polling the status see active work
        Diagnostic inProgress = Diagnostic.builder()
            .diagnosticId(pending.getDiagnosticId())
            .alertId(pending.getAlertId())
            .status(DiagnosticStatus.IN_PROGRESS)
            .generatedAt(pending.getGeneratedAt())
            .build();
        diagnosticRepository.update(inProgress);

        try {
            // Step 1: Collect diagnostic context from MCP servers (K8s, Kruize, Cryostat)
            DiagnosticContext diagnosticContext = collectContext(alert);

            log.info(LogMessages.Diagnostic.CONTEXT_COLLECTED)
                .field(Fields.DIAGNOSTIC_ID, diagnosticId)
                .field(LogFields.ALERT_ID, alert.getAlertId())
                .field(LogFields.HAS_K8S_CONTEXT, diagnosticContext.hasKubernetesContext())
                .field(LogFields.HAS_KRUIZE_CONTEXT, diagnosticContext.hasKruizeContext())
                .field(LogFields.HAS_CRYOSTAT_CONTEXT, diagnosticContext.hasCryostatContext())
                .log();

            // Step 2: Format context for LLM
            String contextForLLM = diagnosticContext.toString();
            String separator = ContextConstants.SEPARATOR_CHAR.repeat(ContextConstants.SEPARATOR_LENGTH);

            log.info(ContextConstants.NEWLINE + separator + ContextConstants.NEWLINE +
                     ContextConstants.CONTEXT_LOG_HEADER + ContextConstants.NEWLINE +
                     separator + ContextConstants.NEWLINE +
                     contextForLLM +
                     separator + ContextConstants.NEWLINE)
                .field(Fields.DIAGNOSTIC_ID, diagnosticId)
                .log();

            // Step 3: Perform root cause analysis using LLM
            RootCauseAnalysis rca = performRootCauseAnalysis(alert, contextForLLM);

            // Step 4: Mark VALIDATING — RCA done, validation about to start
            Diagnostic validating = Diagnostic.builder()
                .diagnosticId(pending.getDiagnosticId())
                .alertId(pending.getAlertId())
                .status(DiagnosticStatus.VALIDATING)
                .generatedAt(pending.getGeneratedAt())
                .build();
            diagnosticRepository.update(validating);

            log.info("Diagnostic status set to VALIDATING")
                .field("diagnosticId", diagnosticId)
                .field("alertId", alert.getAlertId())
                .log();

            // Step 5: Validate RCA against collected context
            ValidatedRCA validatedRCA = validateRca(alert, rca, contextForLLM);

            log.info(LogMessages.Diagnostic.DIAGNOSTIC_COMPLETED)
                .field("diagnosticId", diagnosticId)
                .field("alertId", alert.getAlertId())
                .field("anomalyType", rca.anomalyType())
                .log();

            // Step 6: Persist completed diagnostic with RCA + validation results
            updateDiagnosticWithValidation(pending, rca, validatedRCA);

            // Step 6: Mark alert PROCESSED — pipeline finished successfully
            alertRepository.updateProcessingStatus(alert.getAlertId(), AlertEntityMapper.STATUS_PROCESSED);

            log.info(LogMessages.Diagnostic.DIAGNOSTIC_PIPELINE_DONE)
                .field("diagnosticId", diagnosticId)
                .field("alertId", alert.getAlertId())
                .log();

        } catch (Exception e) {
            // MCP / LLM failure — update both records and log. Does NOT affect the HTTP response
            // since this runs on a background thread after the response was already sent.
            log.error(LogMessages.Diagnostic.DIAGNOSTIC_PIPELINE_FAILED)
                .field("diagnosticId", diagnosticId)
                .field("alertId", alert.getAlertId())
                .exception(e)
                .log();

            // Mark diagnostic FAILED
            try {
                Diagnostic failed = Diagnostic.builder()
                    .diagnosticId(pending.getDiagnosticId())
                    .alertId(pending.getAlertId())
                    .status(DiagnosticStatus.FAILED)
                    .generatedAt(pending.getGeneratedAt())
                    .build();
                diagnosticRepository.update(failed);
            } catch (Exception ex) {
                log.error(LogMessages.Diagnostic.DIAGNOSTIC_UPDATE_FAILED)
                    .field("diagnosticId", diagnosticId)
                    .exception(ex)
                    .log();
            }

            // Mark alert PROCESSED regardless — it was received and attempted
            try {
                alertRepository.updateProcessingStatus(alert.getAlertId(), AlertEntityMapper.STATUS_PROCESSED);
            } catch (Exception ex) {
                log.error(LogMessages.Alert.ALERT_UPDATE_FAILED)
                    .field("alertId", alert.getAlertId())
                    .exception(ex)
                    .log();
            }
        }
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
            // Build the prompt using YAML template
            String systemPrompt = rcaPromptBuilder.getSystemPrompt();
            String userPrompt = rcaPromptBuilder.buildPrompt(alert, contextString);

            log.info(LogMessages.Diagnostic.RCA_PROMPT_BUILT)
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .field(DiagnosticConstants.FIELD_SYSTEM_PROMPT_LENGTH, systemPrompt.length())
                .field(DiagnosticConstants.FIELD_USER_PROMPT_LENGTH, userPrompt.length())
                .log();

            log.debug("Context and prompts prepared")
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .field(DiagnosticConstants.FIELD_CONTEXT_LENGTH, contextString.length())
                .field(DiagnosticConstants.FIELD_SYSTEM_PROMPT_LENGTH, systemPrompt.length())
                .field(DiagnosticConstants.FIELD_USER_PROMPT_LENGTH, userPrompt.length())
                .log();

            // Build LLM request
            LLMRequest llmRequest = LLMRequest.builder(userPrompt)
                .systemPrompt(systemPrompt)
                .temperature(appConfig.getLlmConfig().getTemperature())
                .maxTokens(appConfig.getLlmConfig().getMaxTokens())
                .build();

            // Call the LLM (works with both LangChain and BobShell)
            LLMResponse llmResponse = promptSender.send(llmRequest);

            log.info(LogMessages.Diagnostic.LLM_RESPONSE_RECEIVED)
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .field("modelUsed", llmResponse.modelUsed())
                .field("inputTokens", llmResponse.inputTokens())
                .field("outputTokens", llmResponse.outputTokens())
                .field("latencyMs", llmResponse.latencyMs())
                .log();

            // Parse JSON response to RootCauseAnalysis
            String responseText = llmResponse.responseText();

            log.debug("Parsing LLM response")
                .field("alertId", alert.getAlertId())
                .field("responseLength", responseText.length())
                .log();

            RootCauseAnalysis rca = parseRcaResponse(responseText);

            log.info(LogMessages.Diagnostic.RCA_GENERATED_SUCCESS)
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .field("anomalyType", rca.anomalyType())
                .field("rcaConfidence", rca.confidenceSummary() != null ? rca.confidenceSummary().rcaConfidenceScore() : null)
                .log();

            return rca;

        } catch (Exception e) {
            log.error(LogMessages.Diagnostic.RCA_GENERATION_FAILED)
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .exception(e)
                .log();
            throw new RuntimeException("Failed to generate RCA for alert: " + alert.getAlertId(), e);
        }
    }

    /**
     * Parses the LLM JSON response into a RootCauseAnalysis object.
     *
     * <p>Strips any opening code fence (```json, ```JSON, ```json5, ``` etc.) by
     * skipping the entire first line if it starts with triple backticks — the language
     * tag is ignored, so all variants are handled uniformly.
     * Also tolerates prose preamble emitted by the LLM after tool calls by
     * extracting only the outermost { ... } block.
     *
     * @param responseText the LLM response text (should be JSON)
     * @return the parsed RCA
     */
    private RootCauseAnalysis parseRcaResponse(String responseText) throws Exception {
        // Extract the outermost JSON object from the response.
        // The pattern handles in order:
        //   1. an optional opening code fence (```<lang>\n) — skipped as a unit
        //   2. any leading prose before the first '{' — consumed by [^{]*
        //   3. trailing text after the last '}' (closing fence, prose) — excluded by greedy .*}
        Matcher jsonMatcher = JsonParsingConstants.JSON_OBJECT_PATTERN.matcher(responseText);
        if (!jsonMatcher.find()) {
            throw new IllegalArgumentException("No JSON object found in LLM response");
        }
        String jsonText = jsonMatcher.group(1);

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
     * Persists a completed diagnostic with the parsed RCA results.
     *
     * <p>Extracts {@code confidenceScore} from {@code rca.confidenceSummary()} and maps
     * {@code anomalyType} to a {@link com.causa.common.constants.DiagnosticConstants.FaultDomain}.
     * This is the base persistence step; call {@link #updateDiagnosticWithValidation} instead
     * when validation results are available so they are layered on top.
     *
     * @param pending the original PENDING diagnostic
     * @param rca     the parsed RCA result
     * @return the updated Diagnostic in COMPLETED status
     */
    private Diagnostic persistCompletedDiagnostic(Diagnostic pending, RootCauseAnalysis rca) {
        try {
            Float confidenceScore = null;
            if (rca.confidenceSummary() != null && rca.confidenceSummary().rcaConfidenceScore() != null) {
                confidenceScore = rca.confidenceSummary().rcaConfidenceScore().floatValue();
            }

            com.causa.common.constants.DiagnosticConstants.FaultDomain faultDomain = null;
            if (rca.anomalyType() != null) {
                try {
                    faultDomain = com.causa.common.constants.DiagnosticConstants.FaultDomain
                        .fromString(rca.anomalyType().name());
                } catch (IllegalArgumentException ignored) {
                    // anomaly type has no matching fault domain — leave null
                }
            }

            Diagnostic completed = Diagnostic.builder()
                .diagnosticId(pending.getDiagnosticId())
                .alertId(pending.getAlertId())
                .status(DiagnosticStatus.COMPLETED)
                .generatedAt(pending.getGeneratedAt())
                .confidenceScore(confidenceScore)
                .faultDomain(faultDomain)
                .rca(rca)
                .build();

            return diagnosticRepository.update(completed);

        } catch (Exception e) {
            log.error(LogMessages.Diagnostic.DIAGNOSTIC_UPDATE_FAILED)
                .field("diagnosticId", pending.getDiagnosticId())
                .exception(e)
                .log();
            // Return pending diagnostic — RCA was still generated, persistence failed
            return pending;
        }
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
            .field(ValidationConstants.LogFields.SUPPORTED_COUNT, validatedRCA.getSupportedAssertions().size())
            .field(ValidationConstants.LogFields.UNSUPPORTED_COUNT, validatedRCA.getUnsupportedAssertions().size())
            .field(ValidationConstants.LogFields.UNKNOWN_COUNT, validatedRCA.getUnknownAssertions().size())
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
     * <p>Reuses {@link #persistCompletedDiagnostic} to extract {@code confidenceScore} and
     * {@code faultDomain} from the RCA, then layers the validation fields
     * ({@code validationResult}, {@code validationData}) on top before persisting.
     *
     * @param pending      the original PENDING diagnostic
     * @param rca          the root cause analysis
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
            // Reuse existing logic: persist RCA fields (confidenceScore, faultDomain, rootCauseAnalysis)
            Diagnostic base = persistCompletedDiagnostic(diagnostic, rca);

            // Determine overall validation result
            String validationResult = determineValidationResult(validatedRCA);

            log.info("Validation result determined")
                .field("validationResult", validationResult)
                .log();

            // Build validation data JSON with clean structure
            com.fasterxml.jackson.databind.node.ObjectNode validationDataNode = objectMapper.createObjectNode();

            // Add dual validation if available
            if (validatedRCA.dualValidation() != null) {
                var dualVal = validatedRCA.dualValidation();

                // Final verdict
                validationDataNode.set("finalVerdict", objectMapper.valueToTree(dualVal.finalVerdict()));

                // Assertion validation
                com.fasterxml.jackson.databind.node.ObjectNode assertionValidation = objectMapper.createObjectNode();
                com.fasterxml.jackson.databind.node.ObjectNode assertionSummary = objectMapper.createObjectNode();
                assertionSummary.put("status", dualVal.assertionBasedVerdict().status().toString());
                assertionSummary.put("confidence", dualVal.assertionBasedVerdict().confidence());
                assertionSummary.put("validationScore", dualVal.assertionBasedVerdict().validationScore());
                assertionValidation.set("summary", assertionSummary);
                assertionValidation.set("results", objectMapper.valueToTree(validatedRCA.validationResults()));
                validationDataNode.set("assertionValidation", assertionValidation);

                // Rule validation
                com.fasterxml.jackson.databind.node.ObjectNode ruleValidation = objectMapper.createObjectNode();
                com.fasterxml.jackson.databind.node.ObjectNode ruleSummary = objectMapper.createObjectNode();
                ruleSummary.put("hypothesis", dualVal.ruleBasedVerdict().getHypothesis());
                ruleSummary.put("status", dualVal.ruleBasedVerdict().getStatus().toString());
                ruleSummary.put("confidence", dualVal.ruleBasedVerdict().getConfidence());
                ruleSummary.put("totalScore", dualVal.ruleBasedVerdict().getTotalScore());
                ruleSummary.put("requiredPassed", dualVal.ruleBasedVerdict().getRequiredPassed());
                ruleSummary.put("requiredTotal", dualVal.ruleBasedVerdict().getRequiredTotal());
                ruleValidation.set("summary", ruleSummary);
                ruleValidation.set("results", objectMapper.valueToTree(dualVal.ruleBasedVerdict().getAllResults()));
                validationDataNode.set("ruleValidation", ruleValidation);
            }

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
                String statusIcon = switch (result.status()) {
                    case SUPPORTED -> "✅";
                    case PARTIALLY_SUPPORTED -> "🟡";
                    case UNSUPPORTED -> "❌";
                    case UNKNOWN -> "❓";
                };
                log.info(String.format("  [%d] %s %s: %s (conf=%.2f, evidence=%d supporting)",
                        i + 1, statusIcon, result.assertion().type(),
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
                if (breakdown != null) {
                    log.info(String.format("  Score Breakdown: Required=%d, Supporting=%d, Exclusion=%d",
                            breakdown.getRequiredScore(),
                            breakdown.getSupportingScore(),
                            breakdown.getExclusionScore()))
                        .log();
                }
            }

            // Layer validation fields on top of the already-persisted base diagnostic
            Diagnostic updated = Diagnostic.builder()
                .diagnosticId(base.getDiagnosticId())
                .alertId(base.getAlertId())
                .status(base.getStatus())
                .generatedAt(base.getGeneratedAt())
                .confidenceScore(base.getConfidenceScore())
                .faultDomain(base.getFaultDomain())
                .rca(base.getRca())
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
            updated = diagnosticRepository.update(updated);

            log.info("Diagnostic updated with validation results")
                .field(LogFields.DIAGNOSTIC_ID, diagnostic.getDiagnosticId())
                .field(ValidationConstants.LogFields.VALIDATION_RESULT, validationResult)
                .field(ValidationConstants.LogFields.CONFIDENCE_SCORE, validatedRCA.summary().averageConfidence())
                .log();

            return updated;

        } catch (Exception e) {
            // Try to log validation data even on failure
            try {
                String validationResult = determineValidationResult(validatedRCA);
                com.fasterxml.jackson.databind.node.ObjectNode validationDataNode = objectMapper.createObjectNode();

                // Build clean validation data structure (same as success path)
                if (validatedRCA.dualValidation() != null) {
                    var dualVal = validatedRCA.dualValidation();

                    // Final verdict
                    validationDataNode.set("finalVerdict", objectMapper.valueToTree(dualVal.finalVerdict()));

                    // Assertion validation
                    com.fasterxml.jackson.databind.node.ObjectNode assertionValidation = objectMapper.createObjectNode();
                    com.fasterxml.jackson.databind.node.ObjectNode assertionSummary = objectMapper.createObjectNode();
                    assertionSummary.put("status", dualVal.assertionBasedVerdict().status().toString());
                    assertionSummary.put("confidence", dualVal.assertionBasedVerdict().confidence());
                    assertionSummary.put("validationScore", dualVal.assertionBasedVerdict().validationScore());
                    assertionValidation.set("summary", assertionSummary);
                    assertionValidation.set("results", objectMapper.valueToTree(validatedRCA.validationResults()));
                    validationDataNode.set("assertionValidation", assertionValidation);

                    // Rule validation
                    com.fasterxml.jackson.databind.node.ObjectNode ruleValidation = objectMapper.createObjectNode();
                    com.fasterxml.jackson.databind.node.ObjectNode ruleSummary = objectMapper.createObjectNode();
                    ruleSummary.put("hypothesis", dualVal.ruleBasedVerdict().getHypothesis());
                    ruleSummary.put("status", dualVal.ruleBasedVerdict().getStatus().toString());
                    ruleSummary.put("confidence", dualVal.ruleBasedVerdict().getConfidence());
                    ruleSummary.put("totalScore", dualVal.ruleBasedVerdict().getTotalScore());
                    ruleSummary.put("requiredPassed", dualVal.ruleBasedVerdict().getRequiredPassed());
                    ruleSummary.put("requiredTotal", dualVal.ruleBasedVerdict().getRequiredTotal());
                    ruleValidation.set("summary", ruleSummary);
                    ruleValidation.set("results", objectMapper.valueToTree(dualVal.ruleBasedVerdict().getAllResults()));
                    validationDataNode.set("ruleValidation", ruleValidation);
                }

                validationDataNode.put("validatedAt", validatedRCA.validatedAt().toString());
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(validationDataNode);

                log.error("\n" + "=".repeat(80) + "\n" +
                         "📝 ASSERTIONS VALIDATED (" + validatedRCA.validationResults().size() + " total) - FAILED TO SAVE\n" +
                         "=".repeat(80))
                    .log();

                for (int i = 0; i < validatedRCA.validationResults().size(); i++) {
                    var result = validatedRCA.validationResults().get(i);
                    String statusIcon = switch (result.status()) {
                        case SUPPORTED -> "✅";
                        case PARTIALLY_SUPPORTED -> "🟡";
                        case UNSUPPORTED -> "❌";
                        case UNKNOWN -> "❓";
                    };
                    log.error(String.format("  [%d] %s %s: %s (conf=%.2f, evidence=%d supporting)",
                            i + 1, statusIcon, result.assertion().type(),
                            result.assertion().text(), result.confidence(),
                            result.supportingEvidence().size()))
                        .log();
                }

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
                    if (breakdown != null) {
                        log.error(String.format("  Score Breakdown: Required=%d, Supporting=%d, Exclusion=%d",
                                breakdown.getRequiredScore(),
                                breakdown.getSupportingScore(),
                                breakdown.getExclusionScore()))
                            .log();
                    }
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

    @Override
    public List<Diagnostic> listDiagnostics() {
        return diagnosticRepository.findAll();
    }

    @Override
    public Optional<Diagnostic> getDiagnosticById(String diagnosticId) {
        return diagnosticRepository.findById(diagnosticId);
    }
}
