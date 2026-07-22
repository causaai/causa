package com.causa.core.services.impl;

import com.causa.common.constants.DiagnosticConstants;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;
import com.causa.common.constants.DiagnosticConstants.Fields;
import com.causa.common.constants.DiagnosticConstants.LogFields;
import com.causa.common.constants.JsonParsingConstants;
import com.causa.common.constants.ContextConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.common.utils.IdGenerator;
import com.causa.config.AppConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.DiagnosticContext;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.RootCauseAnalysis;
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
import jakarta.transaction.UserTransaction;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Diagnostic Service Implementation
 *
 * <p>Async execution model — status lifecycle per the agreed spec:
 * <pre>
 *   Alert received         → alert: ACCEPTED,    diagnostic: —
 *   triggerDiagnostics()   → alert: ACCEPTED,    diagnostic: PENDING     (returned immediately)
 *   pipeline starts        → alert: PROCESSING,  diagnostic: IN_PROGRESS
 *   RCA done               → alert: PROCESSING,  diagnostic: VALIDATING  (RCA visible in API)
 *   validation done        → alert: PROCESSED,   diagnostic: COMPLETED / FAILED
 * </pre>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticServiceImpl implements DiagnosticService {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticServiceImpl.class);

    /**
     * Cached thread pool — one thread per in-flight diagnostic.
     * Daemon threads so they do not prevent JVM shutdown.
     */
    private static final ExecutorService PIPELINE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "diag-pipeline");
        t.setDaemon(true);
        return t;
    });

    private final DiagnosticRepository diagnosticRepository;
    private final AlertRepository alertRepository;
    private final McpContextCollector mcpContextCollector;
    private final RcaPromptBuilder rcaPromptBuilder;
    private final PromptSender promptSender;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final UserTransaction userTransaction;
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
                                  UserTransaction userTransaction,
                                  Instance<RcaValidator> rcaValidatorInstance) {
        this.diagnosticRepository = diagnosticRepository;
        this.alertRepository      = alertRepository;
        this.mcpContextCollector  = mcpContextCollector;
        this.rcaPromptBuilder     = rcaPromptBuilder;
        this.promptSender         = promptSender;
        this.appConfig            = appConfig;
        this.objectMapper         = objectMapper;
        this.validator            = validator;
        this.userTransaction      = userTransaction;
        this.rcaValidator         = rcaValidatorInstance.isResolvable() ?
            Optional.of(rcaValidatorInstance.get()) : Optional.empty();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Persists a PENDING diagnostic (id + alert_id + status only) and returns immediately.
     * The full analysis pipeline runs on a background thread — HTTP response is never blocked.
     */
    @Override
    public Diagnostic triggerDiagnostics(Alert alert) {
        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field("alertName", alert.getAlertName())
            .log();

        Instant now = Instant.now();
        String diagnosticId = IdGenerator.diagnosticId();

        // Persist minimal PENDING stub — only id, alert_id, status
        Diagnostic pending = Diagnostic.builder()
            .diagnosticId(diagnosticId)
            .alertId(alert.getAlertId())
            .status(DiagnosticStatus.PENDING)
            .generatedAt(now)
            .build();

        diagnosticRepository.save(pending);

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_INITIATED)
            .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field(LogFields.STATUS, DiagnosticStatus.PENDING.getValue())
            .log();

        // Fire-and-forget — dispatch pipeline to background thread
        PIPELINE_EXECUTOR.submit(() -> runPipeline(alert, pending));

        return pending;
    }

    @Override
    public List<Diagnostic> listDiagnostics() {
        return diagnosticRepository.findAll();
    }

    @Override
    public Optional<Diagnostic> getDiagnosticById(String diagnosticId) {
        return diagnosticRepository.findById(diagnosticId);
    }

    // =========================================================================
    // Background pipeline
    // =========================================================================

    /**
     * Full analysis pipeline — runs on a background thread after {@link #triggerDiagnostics}.
     *
     * <p>Status transitions:
     * <ol>
     *   <li>PENDING → IN_PROGRESS  : before MCP context collection; alert → PROCESSING</li>
     *   <li>IN_PROGRESS → VALIDATING : RCA complete; RCA is now visible in GET /diagnostics/{id}</li>
     *   <li>VALIDATING → COMPLETED  : validation step done; alert → PROCESSED</li>
     *   <li>any → FAILED            : on any uncaught exception; alert → PROCESSED</li>
     * </ol>
     */
    private void runPipeline(Alert alert, Diagnostic pending) {
        String diagnosticId = pending.getDiagnosticId();
        String alertId      = alert.getAlertId();

        log.info(LogMessages.Diagnostic.DIAGNOSTIC_PIPELINE_START)
            .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
            .field(LogFields.ALERT_ID, alertId)
            .log();

        try {
            // ── Step 1: PENDING → IN_PROGRESS; alert → PROCESSING ───────────
            inTx(() -> {
                updateDiagnosticStatus(pending, DiagnosticStatus.IN_PROGRESS);
                alertRepository.updateProcessingStatus(alertId, AlertEntityMapper.STATUS_PROCESSING);
            });

            // ── Step 2: Collect MCP context (no DB, no tx needed) ────────────
            log.info(LogMessages.Diagnostic.CONTEXT_COLLECTION_STARTED)
                .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
                .field(LogFields.ALERT_ID, alertId)
                .log();

            DiagnosticContext context = mcpContextCollector.collectContext(alert);

            log.info(LogMessages.Diagnostic.CONTEXT_COLLECTED)
                .field(Fields.DIAGNOSTIC_ID, diagnosticId)
                .field(LogFields.ALERT_ID, alertId)
                .field(LogFields.HAS_K8S_CONTEXT,      context.hasKubernetesContext())
                .field(LogFields.HAS_KRUIZE_CONTEXT,   context.hasKruizeContext())
                .field(LogFields.HAS_CRYOSTAT_CONTEXT, context.hasCryostatContext())
                .log();

            // ── Step 3: LLM root cause analysis (no DB, no tx needed) ────────
            String contextStr = context.toString();
            String separator  = ContextConstants.SEPARATOR_CHAR.repeat(ContextConstants.SEPARATOR_LENGTH);

            log.info(ContextConstants.NEWLINE + separator + ContextConstants.NEWLINE
                    + ContextConstants.CONTEXT_LOG_HEADER + ContextConstants.NEWLINE
                    + separator + ContextConstants.NEWLINE
                    + contextStr
                    + separator + ContextConstants.NEWLINE)
                .field(Fields.DIAGNOSTIC_ID, diagnosticId)
                .log();

            RootCauseAnalysis rca = performRca(alert, contextStr);

            // ── Step 4: IN_PROGRESS → VALIDATING; persist RCA ────────────────
            Diagnostic withRca = buildWithRca(pending, DiagnosticStatus.VALIDATING, rca);
            inTx(() -> diagnosticRepository.update(withRca));

            log.info("Diagnostic status → VALIDATING; RCA persisted")
                .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
                .field(LogFields.ALERT_ID, alertId)
                .log();

            // ── Step 5: Validate RCA against collected context ───────────────
            ValidatedRCA validatedRCA = validateRca(alert, rca, contextStr);

            // ── Step 6: VALIDATING → COMPLETED; alert → PROCESSED ────────────
            updateDiagnosticWithValidation(withRca, rca, validatedRCA);

            inTx(() -> {
                alertRepository.updateProcessingStatus(alertId, AlertEntityMapper.STATUS_PROCESSED);
            });

            log.info(LogMessages.Diagnostic.DIAGNOSTIC_PIPELINE_DONE)
                .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
                .field(LogFields.ALERT_ID, alertId)
                .log();

        } catch (Exception e) {
            log.error(LogMessages.Diagnostic.DIAGNOSTIC_PIPELINE_FAILED)
                .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
                .field(LogFields.ALERT_ID, alertId)
                .exception(e)
                .log();

            // Mark diagnostic FAILED and alert PROCESSED in a single tx
            safeInTx(() -> {
                safeUpdateStatus(pending, DiagnosticStatus.FAILED);
                alertRepository.updateProcessingStatus(alertId, AlertEntityMapper.STATUS_PROCESSED);
            });
        }
    }

    /**
     * Runs {@code work} inside an explicit JTA transaction.
     * Required because the background thread has no CDI context — {@code @Transactional}
     * interceptors don't fire on plain {@link ExecutorService} threads.
     */
    private void inTx(TxRunnable work) throws Exception {
        userTransaction.begin();
        try {
            work.run();
            userTransaction.commit();
        } catch (Exception e) {
            try { userTransaction.rollback(); } catch (Exception rb) { /* ignore */ }
            throw e;
        }
    }

    /** {@link #inTx} variant that swallows exceptions — used in the catch block. */
    private void safeInTx(TxRunnable work) {
        try { inTx(work); } catch (Exception e) {
            log.error("Failed to persist pipeline failure state")
                .exception(e)
                .log();
        }
    }

    @FunctionalInterface
    private interface TxRunnable {
        void run() throws Exception;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private RootCauseAnalysis performRca(Alert alert, String contextStr) {
        log.debug(LogMessages.Diagnostic.ROOT_CAUSE_ANALYSIS_STARTED)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .log();

        try {
            String systemPrompt = rcaPromptBuilder.getSystemPrompt();
            String userPrompt   = rcaPromptBuilder.buildPrompt(alert, contextStr);

            log.info(LogMessages.Diagnostic.RCA_PROMPT_BUILT)
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .field(DiagnosticConstants.FIELD_SYSTEM_PROMPT_LENGTH, systemPrompt.length())
                .field(DiagnosticConstants.FIELD_USER_PROMPT_LENGTH, userPrompt.length())
                .log();

            LLMRequest req = LLMRequest.builder(userPrompt)
                .systemPrompt(systemPrompt)
                .temperature(appConfig.getLlmConfig().getTemperature())
                .maxTokens(appConfig.getLlmConfig().getMaxTokens())
                .build();

            LLMResponse resp = promptSender.send(req);

            log.info(LogMessages.Diagnostic.LLM_RESPONSE_RECEIVED)
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .field("modelUsed",    resp.modelUsed())
                .field("inputTokens",  resp.inputTokens())
                .field("outputTokens", resp.outputTokens())
                .field("latencyMs",    resp.latencyMs())
                .log();

            RootCauseAnalysis rca = parseRca(resp.responseText());

            log.info(LogMessages.Diagnostic.RCA_GENERATED_SUCCESS)
                .field(DiagnosticConstants.FIELD_ALERT_ID, alert.getAlertId())
                .field("anomalyType", rca.anomalyType())
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

    private RootCauseAnalysis parseRca(String responseText) throws Exception {
        String json = responseText.trim();

        if (json.startsWith(JsonParsingConstants.CODE_BLOCK_PREFIX)) {
            int nl = json.indexOf('\n');
            if (nl > 0) json = json.substring(nl + 1);
        }
        if (json.endsWith(JsonParsingConstants.CODE_BLOCK_PREFIX)) {
            json = json.substring(0, json.length() - JsonParsingConstants.CODE_BLOCK_PREFIX_LENGTH);
        }
        json = json.trim();

        RootCauseAnalysis rca = objectMapper.readValue(json, RootCauseAnalysis.class);

        Set<ConstraintViolation<RootCauseAnalysis>> violations = validator.validate(rca);
        if (!violations.isEmpty()) {
            StringBuilder msg = new StringBuilder("RCA validation failed:");
            violations.forEach(v -> msg.append("\n  - ")
                .append(v.getPropertyPath()).append(": ").append(v.getMessage()));
            throw new IllegalArgumentException(msg.toString());
        }
        return rca;
    }

    /** Builds a new Diagnostic carrying the typed RCA object — no JSON round-trip here. */
    private Diagnostic buildWithRca(Diagnostic base, DiagnosticStatus status, RootCauseAnalysis rca) {
        Float confidenceScore = (rca.confidenceSummary() != null && rca.confidenceSummary().rcaConfidenceScore() != null)
            ? rca.confidenceSummary().rcaConfidenceScore().floatValue() : null;

        FaultDomain faultDomain = null;
        if (rca.anomalyType() != null) {
            try { faultDomain = FaultDomain.fromString(rca.anomalyType().name()); }
            catch (IllegalArgumentException ignored) {}
        }

        return Diagnostic.builder()
            .diagnosticId(base.getDiagnosticId())
            .alertId(base.getAlertId())
            .status(status)
            .generatedAt(base.getGeneratedAt())
            .confidenceScore(confidenceScore)
            .faultDomain(faultDomain)
            .rca(rca)
            .build();
    }

    /** Updates only the status of an existing diagnostic, carrying all other fields through. */
    private void updateDiagnosticStatus(Diagnostic base, DiagnosticStatus newStatus) {
        Diagnostic updated = Diagnostic.builder()
            .diagnosticId(base.getDiagnosticId())
            .alertId(base.getAlertId())
            .status(newStatus)
            .generatedAt(base.getGeneratedAt())
            .confidenceScore(base.getConfidenceScore())
            .faultDomain(base.getFaultDomain())
            .rca(base.getRca())
            .validationResult(base.getValidationResult())
            .validationData(base.getValidationData())
            .build();
        diagnosticRepository.update(updated);
    }

    /** Status-only update that swallows exceptions — used in the catch block. */
    private void safeUpdateStatus(Diagnostic base, DiagnosticStatus newStatus) {
        try { updateDiagnosticStatus(base, newStatus); }
        catch (Exception ex) {
            log.error(LogMessages.Diagnostic.DIAGNOSTIC_UPDATE_FAILED)
                .field(LogFields.DIAGNOSTIC_ID, base.getDiagnosticId())
                .exception(ex)
                .log();
        }
    }

    /**
     * Validates RCA output against collected diagnostic context.
     *
     * <p>Uses assertion-driven validation to verify each claim in the RCA
     * against the collected diagnostic context.
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

            // Layer validation fields on top of the base diagnostic
            Diagnostic updated = Diagnostic.builder()
                .diagnosticId(diagnostic.getDiagnosticId())
                .alertId(diagnostic.getAlertId())
                .status(DiagnosticStatus.COMPLETED)
                .generatedAt(diagnostic.getGeneratedAt())
                .confidenceScore(diagnostic.getConfidenceScore())
                .faultDomain(diagnostic.getFaultDomain())
                .rca(diagnostic.getRca())
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
            inTx(() -> diagnosticRepository.update(updated));

            log.info("Diagnostic updated with validation results")
                .field(LogFields.DIAGNOSTIC_ID, diagnostic.getDiagnosticId())
                .field(LogFields.VALIDATION_RESULT, validationResult)
                .field(LogFields.CONFIDENCE_SCORE, validatedRCA.summary().averageConfidence())
                .log();

            return updated;

        } catch (Exception e) {
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
}
