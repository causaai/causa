package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for performing root cause analysis on detected anomalies.
 * <p>
 * This service takes a detected anomaly type and comprehensive pod context data to perform
 * deep analysis and determine the underlying root cause of the issue. It provides detailed
 * reasoning and proposes actionable solutions to remediate the problem.
 * </p>
 * <p>
 * The analysis heavily focuses on JFR (Java Flight Recorder) data when available, along with
 * metrics, logs, events, and pod status information to provide comprehensive diagnostics.
 * This is the second step in the RCA pipeline, following anomaly detection.
 * </p>
 *
 * @see AnomalyDetector
 * @see ValidationAgent
 */
@RegisterAiService(modelName = "rca")
public interface RootCauseAnalyst {

    /**
     * Analyzes the root cause of a detected anomaly and proposes a solution.
     * <p>
     * Performs deep analysis using all available context including metrics, logs, events,
     * pod status, and JFR data to determine why the anomaly occurred and what can be done
     * to fix it. The analysis includes detailed reasoning and evidence-based conclusions.
     * </p>
     *
     * @param anomalyType the type of anomaly detected (e.g., "OOM_KILLED", "CPU_THROTTLING")
     * @param fullContext the complete context string containing all collected data including
     *                    pod status, events, metrics, logs, and JFR analysis
     * @return a detailed analysis string containing the root cause explanation and proposed
     *         solution with reasoning and evidence
     */
    @UserMessage("You are the Root Cause Analyst. Use all provided context to provide a detailed, reasoned RCA and proposed fix. Focus heavily on the JFR data.\n\nANOMALY TYPE: {anomalyType}\nFULL CONTEXT: {fullContext}\n\nYour task: Determine the root cause and propose a solution. Output only the detailed analysis and fix.")
    String analyzeRootCause(@V("anomalyType") String anomalyType, @V("fullContext") String fullContext);
}
