package com.causa.core.services.impl;

import com.causa.common.utils.IdGenerator;
import com.causa.common.constants.DiagnosticConstants;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.JsonParsingConstants;
import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.DiagnosticConstants.Fields;
import com.causa.common.constants.McpConstants.LogFields;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.DiagnosticService;
import com.causa.core.services.RcaPromptBuilder;
import com.causa.mcp.McpContextCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import com.causa.core.domain.DiagnosticContext;

import java.time.Instant;
import java.util.List;
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
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Inject
    public DiagnosticServiceImpl(DiagnosticRepository diagnosticRepository,
                                  McpContextCollector mcpContextCollector,
                                  RcaPromptBuilder rcaPromptBuilder,
                                  PromptSender promptSender,
                                  AppConfig appConfig,
                                  ObjectMapper objectMapper,
                                  Validator validator) {
        this.diagnosticRepository = diagnosticRepository;
        this.mcpContextCollector = mcpContextCollector;
        this.rcaPromptBuilder = rcaPromptBuilder;
        this.promptSender = promptSender;
        this.appConfig = appConfig;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public Diagnostic triggerDiagnostics(Alert alert) {
        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED)
            .field("alertId", alert.getAlertId())
            .field("alertName", alert.getAlertName())
            .log();

        // Generate diagnostic ID in diag_<16> format (VARCHAR(21) in DB)
        Instant now = Instant.now();
        String diagnosticId = IdGenerator.diagnosticId();

        // Persist diagnostic with PENDING status immediately — this is returned regardless of LLM outcome
        Diagnostic diagnostic = Diagnostic.builder()
            .diagnosticId(diagnosticId)
            .alertId(alert.getAlertId())
            .status(DiagnosticStatus.PENDING)
            .generatedAt(now)
            .build();

        diagnostic = diagnosticRepository.save(diagnostic);

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED)
            .field("diagnosticId", diagnosticId)
            .field("alertId", alert.getAlertId())
            .field("status", DiagnosticStatus.PENDING.getValue())
            .log();

        // Run the full LLM pipeline in a try/catch — a failure here must NOT bubble up
        // and cancel the HTTP response. The alert and diagnostic row are already persisted.
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

            // TODO: Step 4: Validate RCA against collected context
            // TODO: Step 5: Store RCA result in database

            log.info(LogMessages.Diagnostic.DIAGNOSTIC_COMPLETED)
                .field("diagnosticId", diagnosticId)
                .field("alertId", alert.getAlertId())
                .field("anomalyType", rca.anomalyType())
                .log();

        // Step 5: Persist completed diagnostic with RCA results
        diagnostic = persistCompletedDiagnostic(diagnostic, rca);
        } catch (Exception e) {
            // LLM / MCP failure — log and continue. The diagnostic row stays with PENDING status.
            // The HTTP response is NOT affected.
            log.error(LogMessages.Diagnostic.DIAGNOSTIC_FAILED)
                .field("diagnosticId", diagnosticId)
                .field("alertId", alert.getAlertId())
                .exception(e)
                .log();
        }

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
     * Persists a completed diagnostic with the parsed RCA results.
     *
     * <p>Rebuilds the Diagnostic domain object with COMPLETED status and populated
     * RCA fields, then merges it into the database.
     *
     * @param pending the original PENDING diagnostic
     * @param rca     the parsed RCA result
     * @return the updated Diagnostic in COMPLETED status
     */
    private Diagnostic persistCompletedDiagnostic(Diagnostic pending, RootCauseAnalysis rca) {
        try {
            String rcaJson = objectMapper.writeValueAsString(rca);

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
                .rootCauseAnalysis(rcaJson)
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

    @Override
    public List<Diagnostic> listDiagnostics() {
        return diagnosticRepository.findAll();
    }

    @Override
    public Optional<Diagnostic> getDiagnosticById(String diagnosticId) {
        return diagnosticRepository.findById(diagnosticId);
    }

    /**
     * Validates RCA output against collected diagnostic context.
     *
     * <p>NOTE: This is a placeholder. The actual validation framework is in branch rca-validation-impl.
     *
     * @param alert the alert being analyzed
     * @param rca the RCA result to validate
     * @deprecated Use 3-parameter version: validateRca(Alert, RootCauseAnalysis, String)
     */
    @Deprecated(since = "0.0.1", forRemoval = true)
    private void validateRca(Alert alert, RootCauseAnalysis rca) {
        log.debug(LogMessages.Diagnostic.RCA_VALIDATION_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        // TODO: Remove this placeholder when RCA validation framework is merged
    }
}
