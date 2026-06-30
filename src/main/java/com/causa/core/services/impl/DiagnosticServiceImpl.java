package com.causa.core.services.impl;

import com.causa.common.constants.DiagnosticConstants;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.JsonParsingConstants;
import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.DiagnosticConstants.Fields;
import com.causa.common.constants.McpConstants.LogFields;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.LLMConfig;
import com.causa.config.PrometheusConfig;
import com.causa.core.domain.Alert;
import com.causa.core.ports.PrometheusClient;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
    private final PrometheusClient prometheusClient;
    private final PrometheusConfig prometheusConfig;
    private final RcaPromptBuilder rcaPromptBuilder;
    private final PromptSender promptSender;
    private final LLMConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Inject
    public DiagnosticServiceImpl(DiagnosticRepository diagnosticRepository,
                                  McpContextCollector mcpContextCollector,
                                  PrometheusClient prometheusClient,
                                  PrometheusConfig prometheusConfig,
                                  RcaPromptBuilder rcaPromptBuilder,
                                  PromptSender promptSender,
                                  LLMConfig llmConfig,
                                  ObjectMapper objectMapper,
                                  Validator validator) {
        this.diagnosticRepository = diagnosticRepository;
        this.mcpContextCollector = mcpContextCollector;
        this.prometheusClient = prometheusClient;
        this.prometheusConfig = prometheusConfig;
        this.rcaPromptBuilder = rcaPromptBuilder;
        this.promptSender = promptSender;
        this.llmConfig = llmConfig;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public Diagnostic triggerDiagnostics(Alert alert) {
        log.info(LogMessages.Diagnostic.DIAGNOSTIC_TRIGGERED)
            .field("alertId", alert.getAlertId())
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
            .field("diagnosticId", diagnosticId)
            .field("alertId", alert.getAlertId())
            .field("status", DiagnosticStatus.PENDING.getValue())
            .log();


        // Collect MCP context (Kubernetes, Kruize, Cryostat, JVM logs)
        DiagnosticContext diagnosticContext = collectContext(alert);

        // Enrich with Prometheus metrics
        diagnosticContext = enrichWithPrometheusMetrics(diagnosticContext, alert);

        // Log the complete collected context for visibility
        log.info(LogMessages.Diagnostic.CONTEXT_COLLECTED)
            .field(Fields.DIAGNOSTIC_ID, diagnosticId)
            .field(LogFields.ALERT_ID, alert.getAlertId())
            .field(LogFields.HAS_K8S_CONTEXT, diagnosticContext.hasKubernetesContext())
            .field(LogFields.HAS_KRUIZE_CONTEXT, diagnosticContext.hasKruizeContext())
            .field(LogFields.HAS_CRYOSTAT_CONTEXT, diagnosticContext.hasCryostatContext())
            .field("hasJvmLogs", diagnosticContext.hasJvmLogs())
            .log();

        String contextForLLM = diagnosticContext.toString();
        String separator = ContextConstants.SEPARATOR_CHAR.repeat(ContextConstants.SEPARATOR_LENGTH);
        
        // Log the full formatted context that will be sent to LLM
        log.info(ContextConstants.NEWLINE + separator + ContextConstants.NEWLINE +
                 ContextConstants.CONTEXT_LOG_HEADER + ContextConstants.NEWLINE +
                 separator + ContextConstants.NEWLINE +
                 contextForLLM +
                 separator + ContextConstants.NEWLINE)
            .field(Fields.DIAGNOSTIC_ID, diagnosticId)
            .log();
        
        RootCauseAnalysis rca = performRootCauseAnalysis(alert, contextForLLM);

        try {
            log.info("RCA GENERATED")
                    .field("rca", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rca))
                    .log();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.info("RCA GENERATED")
                    .field("rca", rca)
                    .log();
        }

        // TODO: Store RCA result in database
        // TODO: validateRca(alert, rca);
        // determineDiagnosisType(alert);
        // performRootCauseAnalysis(alert);
        validateRca(alert, rca);

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
                .field("systemPromptLength", systemPrompt.length())
                .field("userPromptLength", userPrompt.length())
                .log();

            log.debug("Context and prompts prepared")
                .field("alertId", alert.getAlertId())
                .field("contextLength", contextString.length())
                .field("systemPromptLength", systemPrompt.length())
                .field("userPromptLength", userPrompt.length())
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
                .field("rcaConfidence", rca.llmConfidenceScoreForRca())
                .field("solutionConfidence", rca.llmConfidenceScoreForSolution())
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

        // Clean up empty strings from arrays (defensive filter in case LLM doesn't follow prompt)
        rca = cleanEmptyStrings(rca);

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
     * Filters out empty strings from supporting_logs and evidences arrays.
     * Defensive cleanup in case LLM doesn't follow prompt instructions.
     *
     * @param rca the parsed RCA object
     * @return new RCA with empty strings filtered out
     */
    private RootCauseAnalysis cleanEmptyStrings(RootCauseAnalysis rca) {
        if (rca == null) {
            return null;
        }

        // Filter empty strings from supporting_logs
        List<String> cleanedLogs = rca.supportingLogs() != null
            ? rca.supportingLogs().stream()
                .filter(log -> log != null && !log.trim().isEmpty())
                .toList()
            : List.of();

        // Filter empty strings from evidences
        List<String> cleanedEvidences = rca.evidences() != null
            ? rca.evidences().stream()
                .filter(evidence -> evidence != null && !evidence.trim().isEmpty())
                .toList()
            : List.of();

        // Return new RCA with cleaned arrays
        return new RootCauseAnalysis(
            rca.issueTitle(),
            rca.issueDescription(),
            rca.technicalDescription(),
            rca.anomalyType(),
            rca.rootCause(),
            cleanedLogs,
            cleanedEvidences,
            rca.possibleSolutions(),
            rca.llmConfidenceScoreForRca(),
            rca.llmConfidenceScoreForSolution(),
            rca.confidenceSummary(),
            rca.llmNotes()
        );
    }

    /**
     * Validates LLM output using hybrid validation engine.
     *
     * <p>Future implementation will:
     * <ul>
     *   <li>Verify LLM provided evidence citations</li>
     *   <li>Apply deterministic sanity checks against metrics</li>
     *   <li>Run critic LLM for adversarial validation</li>
     * </ul>
     *
     * @param alert the alert being analyzed
     * @param rca the RCA result to validate
     */
    private void validateRca(Alert alert, RootCauseAnalysis rca) {
        log.debug(LogMessages.Diagnostic.RCA_VALIDATION_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        // TODO: Implement hybrid validation
        // - Evidence assertion verification
        // - Rule-based metric validation
        // - Optional critic LLM pass
    }

    /**
     * Enriches diagnostic context with Prometheus time-series metrics.
     *
     * <p>Collects memory, CPU, and GC metrics for the specified lookback period.
     * <p>Returns a new DiagnosticContext with Prometheus metrics added.
     *
     * @param context the existing context from MCP servers
     * @param alert the alert being diagnosed
     * @return enriched context with Prometheus metrics, or original context if collection fails
     */
    private DiagnosticContext enrichWithPrometheusMetrics(DiagnosticContext context, Alert alert) {
        if (!prometheusConfig.enabled()) {
            log.debug("Prometheus metrics collection is disabled");
            return context;
        }

        try {
            // Extract Prometheus URL from alert's generatorURL
            String prometheusUrl = extractPrometheusUrl(alert);
            if (prometheusUrl == null) {
                log.debug("No Prometheus URL available for metrics collection")
                    .field("alertId", alert.getAlertId())
                    .log();
                return context;
            }

            // Collect metrics
            String metrics = prometheusClient.collectMemoryMetrics(
                prometheusUrl,
                alert.getNamespace(),
                alert.getPodName(),
                alert.getContainerName(),
                alert.getTimestamp(),
                prometheusConfig.lookbackHours()
            );

            if (metrics != null && !metrics.isBlank()) {
                log.info("Prometheus metrics collected")
                    .field("alertId", alert.getAlertId())
                    .field("podName", alert.getPodName())
                    .field("prometheusUrl", prometheusUrl)
                    .log();

                // Create new context with Prometheus metrics
                return DiagnosticContext.builder()
                    .podName(context.getPodName())
                    .containerName(context.getContainerName())
                    .namespace(context.getNamespace())
                    .podStatus(context.getPodStatus())
                    .podEvents(context.getPodEvents())
                    .podLogs(context.getPodLogs())
                    .costRecommendations(context.getCostRecommendations())
                    .performanceRecommendations(context.getPerformanceRecommendations())
                    .gcAnalysis(context.getGcAnalysis())
                    .memoryAnalysis(context.getMemoryAnalysis())
                    .threadAnalysis(context.getThreadAnalysis())
                    .exceptionAnalysis(context.getExceptionAnalysis())
                    .containerAnalysis(context.getContainerAnalysis())
                    .verboseGcLog(context.getVerboseGcLog())
                    .jitLog(context.getJitLog())
                    .javacoreDump(context.getJavacoreDump())
                    .prometheusMetrics(metrics)  // Add Prometheus metrics
                    .build();
            }

            return context;

        } catch (Exception e) {
            log.warn("Failed to enrich context with Prometheus metrics")
                .field("alertId", alert.getAlertId())
                .field("error", e.getMessage())
                .log();
            return context;  // Return original context on failure
        }
    }

    /**
     * Extracts metrics URL from alert.
     *
     * <p>Tries in order:
     * <ol>
     *   <li>'metrics_url' label (application /metrics endpoint)</li>
     *   <li>'prometheus_url' label (custom override)</li>
     *   <li>generatorURL from alert (cluster Prometheus - may require auth)</li>
     *   <li>Config override</li>
     * </ol>
     *
     * @param alert the alert
     * @return metrics URL (either app /metrics or Prometheus API), or null if not available
     */
    private String extractPrometheusUrl(Alert alert) {
        if (alert.getLabels() != null) {
            // Prefer app metrics endpoint (no auth required)
            String metricsUrl = alert.getLabels().get("metrics_url");
            if (metricsUrl != null && !metricsUrl.isBlank()) {
                log.debug("Using application metrics endpoint from alert")
                    .field("metricsUrl", metricsUrl)
                    .log();
                return metricsUrl;
            }

            // Try custom prometheus_url
            String prometheusUrl = alert.getLabels().get("prometheus_url");
            if (prometheusUrl != null && !prometheusUrl.isBlank()) {
                return prometheusUrl;
            }

            // Try generatorURL from labels (AlertMapper stores it there)
            String generatorUrl = alert.getLabels().get("generatorURL");
            if (generatorUrl != null && !generatorUrl.isBlank()) {
                String extracted = prometheusClient.extractPrometheusUrl(generatorUrl);
                if (extracted != null) {
                    log.debug("Extracted Prometheus URL from generatorURL")
                        .field("generatorURL", generatorUrl)
                        .field("extracted", extracted)
                        .log();
                    return extracted;
                }
            }
        }

        // Fallback to config override
        return prometheusConfig.urlOverride().orElse(null);
    }
}
