package com.causa.rca.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import com.causa.rca.ai.AnomalyDetector;
import com.causa.rca.ai.RootCauseAnalyst;
import com.causa.rca.ai.ValidationAgent;
import com.causa.rca.model.RcaReport;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ContainerStatus;

import java.util.Map;
import java.util.List;

/**
 * Orchestrator service that coordinates the complete RCA (Root Cause Analysis) pipeline.
 * <p>
 * This service implements a three-step AI-powered analysis workflow:
 * <ol>
 *   <li><b>Data Collection:</b> Gathers comprehensive diagnostic data from multiple sources</li>
 *   <li><b>Anomaly Detection:</b> Uses AI to identify if the pod has any issues</li>
 *   <li><b>Root Cause Analysis:</b> Performs deep analysis to determine the underlying cause</li>
 *   <li><b>Validation & Formatting:</b> Validates the analysis and formats it into a structured report</li>
 * </ol>
 * </p>
 * <p>
 * The orchestrator handles the complete flow from raw data collection through AI analysis
 * to final report generation, including special handling for healthy systems and error cases.
 * </p>
 *
 * @see DataCollectorService
 * @see AnomalyDetector
 * @see RootCauseAnalyst
 * @see ValidationAgent
 * @see RcaReport
 */
@ApplicationScoped
public class RcaOrchestrator {

    private static final Logger LOG = Logger.getLogger(RcaOrchestrator.class);

    @Inject
    DataCollectorService dataCollector;

    @Inject
    AnomalyDetector anomalyDetector;

    @Inject
    RootCauseAnalyst rootCauseAnalyst;

    @Inject
    ValidationAgent reportValidator;

    /**
     * Executes the complete RCA analysis pipeline for a specific pod.
     * <p>
     * The analysis proceeds through the following steps:
     * <ol>
     *   <li>Collects comprehensive data (metrics, logs, events, JFR, pod status)</li>
     *   <li>Detects anomalies using AI analysis of the collected data</li>
     *   <li>If healthy, returns immediately with a healthy status report</li>
     *   <li>If anomaly detected, performs root cause analysis with detailed reasoning</li>
     *   <li>Validates and formats the analysis into a structured {@link RcaReport}</li>
     * </ol>
     * </p>
     * <p>
     * The method includes sanitization of AI outputs to handle potential formatting
     * inconsistencies and ensures robust error handling throughout the pipeline.
     * </p>
     *
     * @param namespace the Kubernetes namespace where the pod is located
     * @param podName the name of the pod to analyze
     * @return a complete {@link RcaReport} containing the analysis results, including
     *         issue description, evidence, proposed solution, and confidence score
     */
    public RcaReport runAnalysis(String namespace, String podName) {
        LOG.info("Starting RCA analysis for: " + namespace + "/" + podName);

        // Step 0: Collect Data
        Map<String, String> data = dataCollector.getRealDataPackage(namespace, podName);
        String metricsData = data.get("metrics_data");
        String fullContext = data.get("full_context");
        LOG.info("Data collection complete. Metrics summary length: " + (metricsData != null ? metricsData.length() : 0)
                + ", Full context length: " + (fullContext != null ? fullContext.length() : 0));

        // Step 1: Anomaly Detection
        LOG.info("Step 1: Running Anomaly Detection...");
        LOG.debug("Context for Anomaly Detection:\n" + fullContext);
        String rawAnomaly = anomalyDetector.detectAnomaly(fullContext);
        LOG.info("RAW Anomaly Detector Response: [" + rawAnomaly + "]");

        // Sanitize LLM output to get the core anomaly type (first line, before any comments)
        String anomalyType = rawAnomaly.split("\n")[0].split("#")[0].trim();
        LOG.info("Sanitized Anomaly Type: [" + anomalyType + "]");

        if (anomalyType.isEmpty() || "HEALTHY".equalsIgnoreCase(anomalyType)
                || anomalyType.toUpperCase().contains("HEALTHY")) {
            LOG.info("System is healthy or no anomaly detected. Skipping RCA and Validation.");
            return new RcaReport("System Healthy", "No anomaly detected", "Metrics within normal range", null,
                    "No action needed", 1.0);
        }

        // Step 2: Root Cause Analysis
        LOG.info("Step 2: Running Root Cause Analysis...");
        LOG.debug("Context for RCA:\n" + fullContext);
        String rcaOutput = rootCauseAnalyst.analyzeRootCause(anomalyType, fullContext);
        LOG.info("RAW RCA Result: [" + rcaOutput + "]");

        // Step 3: Validation and Formatting
        LOG.info("Step 3: Running Validation and Formatting...");
        LOG.debug("Context for Validation:\n" + rcaOutput);
        RcaReport report = reportValidator.validateAndFormat(rcaOutput, fullContext);
        LOG.info("Final Report Object: " + report);

        return report;
    }
}
