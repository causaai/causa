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
import com.causa.config.LLMConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.DiagnosticContext;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.DiagnosticService;
import com.causa.core.services.RcaPromptBuilder;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import com.causa.mcp.McpContextCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
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
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final UserTransaction userTransaction;

    @Inject
    public DiagnosticServiceImpl(DiagnosticRepository diagnosticRepository,
                                  AlertRepository alertRepository,
                                  McpContextCollector mcpContextCollector,
                                  RcaPromptBuilder rcaPromptBuilder,
                                  PromptSender promptSender,
                                  LLMConfig llmConfig,
                                  ObjectMapper objectMapper,
                                  Validator validator,
                                  UserTransaction userTransaction) {
        this.diagnosticRepository = diagnosticRepository;
        this.alertRepository      = alertRepository;
        this.mcpContextCollector  = mcpContextCollector;
        this.rcaPromptBuilder     = rcaPromptBuilder;
        this.promptSender         = promptSender;
        this.llmConfig            = llmConfig;
        this.objectMapper         = objectMapper;
        this.validator            = validator;
        this.userTransaction      = userTransaction;
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
        String diagnosticId = Diagnostic.generateDiagnosticId(alert.getAlertId(), now);

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
     *   <li>VALIDATING → COMPLETED  : validation step done (placeholder); alert → PROCESSED</li>
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
            // Each DB call needs its own transaction — this thread has no CDI context,
            // so @Transactional interceptors don't fire. Use UserTransaction explicitly.
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

            // ── Step 5: VALIDATING → COMPLETED; alert → PROCESSED ────────────
            Diagnostic completed = Diagnostic.builder()
                .diagnosticId(withRca.getDiagnosticId())
                .alertId(withRca.getAlertId())
                .status(DiagnosticStatus.COMPLETED)
                .generatedAt(withRca.getGeneratedAt())
                .confidenceScore(withRca.getConfidenceScore())
                .faultDomain(withRca.getFaultDomain())
                .rca(withRca.getRca())
                .build();
            inTx(() -> {
                diagnosticRepository.update(completed);
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
                .temperature(llmConfig.temperature())
                .maxTokens(llmConfig.maxTokens())
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
            .rca(rca)                      // typed — mapper serialises to TEXT on persist
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
}
