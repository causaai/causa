package com.causa.rca.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for detecting anomalies in Kubernetes pods.
 * <p>
 * This service analyzes pod metrics, status, logs, and events to identify potential issues
 * such as OOM (Out of Memory) kills, CPU throttling, crash loops, and other anomalies.
 * It serves as the first step in the RCA (Root Cause Analysis) pipeline.
 * </p>
 * <p>
 * The service uses a specialized LLM model configured with the "detector" model name
 * to perform pattern recognition and anomaly classification based on the provided context.
 * </p>
 *
 * @see RootCauseAnalyst
 * @see ValidationAgent
 */
@RegisterAiService(modelName = "detector")
public interface AnomalyDetector {

    /**
     * Detects anomalies from the provided pod context data.
     * <p>
     * Analyzes comprehensive pod information including metrics, status, logs, and events
     * to identify if the pod is experiencing any issues. The method returns a simple
     * anomaly classification or 'HEALTHY' if no issues are detected.
     * </p>
     *
     * @param context the complete context string containing pod metrics, status, logs,
     *                events, and JFR analysis data formatted for AI analysis
     * @return a string representing the detected anomaly type (e.g., "OOM_KILLED",
     *         "CPU_THROTTLING", "CRASH_LOOP") or "HEALTHY" if no anomaly is detected
     */
    @SystemMessage("You are a specialized anomaly detection model. Analyze the METRICS and POD STATUS data. Output ONLY the anomaly type or 'HEALTHY'. Example: 'OOM_KILLED'.")
    String detectAnomaly(@UserMessage String context);
}