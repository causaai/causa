package com.causa.core.services.impl;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
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

import java.time.Instant;

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
    private final ObjectMapper objectMapper;

    @Inject
    public DiagnosticServiceImpl(DiagnosticRepository diagnosticRepository,
                                  McpContextCollector mcpContextCollector,
                                  RcaPromptBuilder rcaPromptBuilder,
                                  PromptSender promptSender) {
        this.diagnosticRepository = diagnosticRepository;
        this.mcpContextCollector = mcpContextCollector;
        this.rcaPromptBuilder = rcaPromptBuilder;
        this.promptSender = promptSender;
        this.objectMapper = new ObjectMapper();
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

        // TODO: Trigger async diagnostic pipeline
        // For now, just call methods synchronously
        collectContext(alert); // Logs context to console (existing MCP integration)
        String contextForLLM = buildContextForLLM(alert); // Build formatted context for LLM
        RootCauseAnalysis rca = performRootCauseAnalysis(alert, contextForLLM);

        // TODO: Store RCA result in database
        // TODO: validateRca(alert, rca);

        return diagnostic;
    }

    /**
     * Builds context string to be sent to LLM.
     *
     * <p>Constructs a formatted context string from MCP data following the format
     * defined in shekhar316/causa-prompts repository.
     *
     * @param alert the alert to build context for
     * @return formatted context string for LLM
     */
    private String buildContextForLLM(Alert alert) {
        log.debug("Building LLM context")
            .field("alertId", alert.getAlertId())
            .log();

        // TODO: Replace with actual MCP context once merged
        // For now, using test context from causa-prompts for internal testing
        String contextString = buildTestContext(alert);

        // FUTURE: Uncomment when MCP integration is complete
        // String contextString = mcpContextCollector.collectContextAsString(alert);

        log.debug("LLM context built")
            .field("alertId", alert.getAlertId())
            .field("contextLength", contextString.length())
            .log();

        return contextString;
    }

    /**
     * Builds test context for internal testing.
     *
     * <p>This method provides realistic test context based on examples from
     * https://github.com/shekhar316/causa-prompts/blob/main/master-prompt-with-signals.txt
     *
     * <p>Modify this context as needed for testing different scenarios before MCP is fully integrated.
     *
     * @param alert the alert to build context for
     * @return test context string
     */
    private String buildTestContext(Alert alert) {
        // Example context showing OOM scenario with high memory usage
        return """
            ## POD STATUS
            Pod: heap-oom-prom-5785ff66b9-pt87l
            Namespace: chaos-test
            Status: Running

            ## KUBERNETES EVENTS (for pod: heap-oom-prom-5785ff66b9-pt87l)
            [Normal] 2026-06-18 06:40:57 +0000 UTC: AddedInterface - Add eth0 [10.131.0.38/23] from ovn-kubernetes
            [Normal] 2026-06-18 06:40:57 +0000 UTC: Pulling - Pulling image "quay.io/cryostat/cryostat-agent-init:0.7.0"
            [Normal] 2026-06-18 06:40:59 +0000 UTC: Pulled - Successfully pulled image "quay.io/cryostat/cryostat-agent-init:0.7.0"
            [Normal] 2026-06-18 06:40:59 +0000 UTC: Created - Created container: cryostat-agent-init
            [Normal] 2026-06-18 06:40:59 +0000 UTC: Started - Started container cryostat-agent-init
            [Normal] 2026-06-18 08:48:15 +0000 UTC: Pulled - Container image "quay.io/causa-ai-hub/quarkus-heap-oom:heap-oom-prom" already present
            [Normal] 2026-06-18 08:41:44 +0000 UTC: Created - Created container: heap-oom-prom
            [Normal] 2026-06-18 07:01:57 +0000 UTC: Started - Started container heap-oom-prom
            [Warning] 2026-06-18 08:51:11 +0000 UTC: BackOff - Back-off restarting failed container heap-oom-prom in pod heap-oom-prom-5785ff66b9-pt87l_chaos-test(0fa217dc-746e-42a4-bd23-4ee98642bf0d)

            ## PROMETHEUS METRICS
            CPU Usage: 0.071/0.500 cores
            MEMORY Usage: 478/512 MiB
            Collected at time of alert.

            ## KRUIZE RECOMMENDATIONS
            Performance Recommendations:
              resources:
                requests:
                  cpu: 0.435 # +0.185
                  memory: 948 Mi # +692 Mi
                limits:
                  cpu: 0.435 # -0.065
                  memory: 948 Mi # +436 Mi

            ## POD LOGS
            Pod: heap-oom-prom-5785ff66b9-dfww8 | Container: heap-oom-prom
            2026-06-18 09:17:08,004 INFO [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 95000 targets. Current registry size=95000
            2026-06-18 09:17:13,005 INFO [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 100000 targets. Current registry size=100000
            2026-06-18 09:17:18,006 INFO [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 105000 targets. Current registry size=105000
            2026-06-18 09:17:23,007 INFO [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 110000 targets. Current registry size=110000
            2026-06-18 09:17:28,008 INFO [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-1) Inserted 115000 targets. Current registry size=115000

            ## JFR CONTAINER ANALYSIS
            {
              "status": "RECENT_ARCHIVED_EXISTS",
              "events": {
                "jdk.ContainerConfiguration": {
                  "row_count": 1,
                  "rows": [{
                    "containerType": "cgroupv2",
                    "cpuQuota": "50000000",
                    "effectiveCpuCount": "1",
                    "memoryLimit": "536870912",
                    "hostTotalMemory": "65993228288"
                  }]
                }
              }
            }

            ## JFR GC ANALYSIS
            {
              "events": {
                "jdk.GCHeapSummary": {
                  "row_count": 2,
                  "rows": [
                    {"heapUsed": "21360344", "when": "Before GC"},
                    {"heapUsed": "17271584", "when": "After GC"}
                  ]
                },
                "jdk.GCHeapMemoryPoolUsage": {
                  "row_count": 4,
                  "rows": [
                    {"name": "Eden Space", "used": "4088760", "committed": "114688000", "max": "114688000"},
                    {"name": "Survivor Space", "used": "0", "committed": "14680064", "max": "14680064"},
                    {"name": "Tenured Gen", "used": "17271584", "committed": "286654464", "max": "286654464"}
                  ]
                },
                "jdk.GarbageCollection": {
                  "row_count": 1,
                  "rows": [
                    {"name": "DefNew", "duration": "3292885", "cause": "Allocation Failure", "longestPause": "3292885"}
                  ]
                }
              }
            }

            ## JFR MEMORY ANALYSIS
            {
              "events": {
                "jdk.JavaMemoryUsage": {
                  "row_count": 3,
                  "rows": [
                    {"memoryType": "Heap", "used": "17271584", "committed": "416022528", "max": "416022528"},
                    {"memoryType": "Non-heap", "used": "50217176", "committed": "53600256", "max": "-1"}
                  ]
                }
              }
            }

            ## JFR THREAD ANALYSIS
            {
              "events": {
                "jdk.JavaThreadStatistics": {
                  "row_count": 1,
                  "rows": [
                    {"activeCount": "23", "daemonCount": "19", "accumulatedCount": "29", "peakCount": "24"}
                  ]
                }
              }
            }

            ## JFR EXCEPTION ANALYSIS
            {
              "events": {
                "jdk.ExceptionStatistics": {
                  "row_count": 2,
                  "rows": [
                    {"throwables": "java.lang.OutOfMemoryError", "count": "1"},
                    {"throwables": "java.lang.NullPointerException", "count": "3"}
                  ]
                }
              }
            }
            """;
    }

    /**
     * Collects context from MCP servers and logs results.
     *
     * <p>Calls Kubernetes MCP for pod status, events, and logs.
     * This method logs context to console for debugging.
     *
     * @param alert the alert to collect context for
     */
    private void collectContext(Alert alert) {
        log.debug(LogMessages.Diagnostic.CONTEXT_COLLECTION_STARTED)
            .field("alertId", alert.getAlertId())
            .log();

        mcpContextCollector.collectAndLogContext(alert);
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

            log.info("RCA prompt built")
                .field("alertId", alert.getAlertId())
                .field("systemPromptLength", systemPrompt.length())
                .field("userPromptLength", userPrompt.length())
                .log();

            // DEBUG: Print context being sent to LLM
            System.out.println("\n========== CONTEXT SENT TO LLM (Alert: " + alert.getAlertId() + ") ==========");
            System.out.println(contextString);
            System.out.println("\n========== SYSTEM PROMPT ==========");
            System.out.println(systemPrompt);
            System.out.println("========================================\n");

            // Build LLM request
            LLMRequest llmRequest = LLMRequest.builder(userPrompt)
                .systemPrompt(systemPrompt)
                .temperature(0.1)  // Low temperature for deterministic RCA
                .maxTokens(4096)
                .build();

            // Call the LLM (works with both LangChain and BobShell)
            LLMResponse llmResponse = promptSender.send(llmRequest);

            log.info("LLM response received")
                .field("alertId", alert.getAlertId())
                .field("modelUsed", llmResponse.modelUsed())
                .field("inputTokens", llmResponse.inputTokens())
                .field("outputTokens", llmResponse.outputTokens())
                .field("latencyMs", llmResponse.latencyMs())
                .log();

            // Parse JSON response to RootCauseAnalysis
            String responseText = llmResponse.responseText();

            // DEBUG: Print LLM response
            System.out.println("\n========== RAW LLM RESPONSE (Alert: " + alert.getAlertId() + ") ==========");
            System.out.println(responseText);
            System.out.println("========================================\n");

            RootCauseAnalysis rca = parseRcaResponse(responseText);

            // DEBUG: Print parsed RCA summary
            System.out.println("\n========== PARSED RCA OUTPUT ==========");
            System.out.println("Alert ID: " + alert.getAlertId());
            System.out.println("Issue Title: " + rca.issueTitle());
            System.out.println("Anomaly Type: " + rca.anomalyType());
            System.out.println("Root Cause: " + rca.rootCause());
            System.out.println("RCA Confidence: " + rca.llmConfidenceScoreForRca());
            System.out.println("Solution Confidence: " + rca.llmConfidenceScoreForSolution());
            System.out.println("Number of Solutions: " + rca.possibleSolutions().size());
            System.out.println("Solutions:");
            for (int i = 0; i < rca.possibleSolutions().size(); i++) {
                var sol = rca.possibleSolutions().get(i);
                System.out.println("  " + (i + 1) + ". " + sol.solution() + " (Probability: " + sol.successProbability() + ")");
            }
            System.out.println("========================================\n");

            log.info("RCA generated successfully")
                .field("alertId", alert.getAlertId())
                .field("anomalyType", rca.anomalyType())
                .field("rcaConfidence", rca.llmConfidenceScoreForRca())
                .field("solutionConfidence", rca.llmConfidenceScoreForSolution())
                .log();

            return rca;

        } catch (Exception e) {
            log.error("RCA generation failed")
                .field("alertId", alert.getAlertId())
                .exception(e)
                .log();
            throw new RuntimeException("Failed to generate RCA for alert: " + alert.getAlertId(), e);
        }
    }

    /**
     * Parses the LLM JSON response into a RootCauseAnalysis object.
     *
     * @param responseText the LLM response text (should be JSON)
     * @return the parsed RCA
     */
    private RootCauseAnalysis parseRcaResponse(String responseText) throws Exception {
        // Clean the response - remove markdown code blocks if present
        String jsonText = responseText.trim();
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.substring(7);
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.substring(3);
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.substring(0, jsonText.length() - 3);
        }
        jsonText = jsonText.trim();

        // Parse JSON to RootCauseAnalysis
        return objectMapper.readValue(jsonText, RootCauseAnalysis.class);
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
}
