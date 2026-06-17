package com.causa.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

/**
 * Causa RCA Metrics
 *
 * Exposes Prometheus metrics for Datadog monitoring matching the configured monitors:
 * - causa_rca_analysis_failed: Failed RCA analyses (with reason, analysis_type, namespace tags)
 * - causa_rca_analysis_duration_seconds: Duration of RCA analysis (with analysis_type, namespace tags)
 * - causa_analysis_count: Total count of all analyses
 * - causa_rca_analysis_completed_total: Total completed RCA analyses
 */
@ApplicationScoped
public class CausaMetrics {

    private final MeterRegistry registry;
    private final Counter analysisCountCounter;
    private final Counter analysisCompletedCounter;

    @Inject
    public CausaMetrics(MeterRegistry registry) {
        this.registry = registry;

        // General analysis count (used by "No Analysis Generated" monitor)
        this.analysisCountCounter = Counter.builder("causa_analysis_count")
                .description("Total count of all RCA analyses")
                .tag("app", "causa")
                .register(registry);

        // Completed analyses counter
        this.analysisCompletedCounter = Counter.builder("causa_rca_analysis_completed_total")
                .description("Total number of completed RCA analyses")
                .tag("app", "causa")
                .register(registry);
    }

    /**
     * Record a completed RCA analysis
     *
     * @param analysisType Type of analysis (e.g., "pod_crash", "high_cpu")
     * @param namespace Kubernetes namespace
     * @param durationSeconds Duration in seconds
     */
    public void recordCompleted(String analysisType, String namespace, double durationSeconds) {
        // Increment general count
        analysisCountCounter.increment();

        // Increment completed counter
        analysisCompletedCounter.increment();

        // Record duration with tags (matches latency monitor query)
        Timer.builder("causa_rca_analysis_duration_seconds")
                .description("Duration of RCA analysis in seconds")
                .tag("app", "causa")
                .tag("analysis_type", analysisType != null ? analysisType : "unknown")
                .tag("namespace", namespace != null ? namespace : "unknown")
                .register(registry)
                .record(Duration.ofMillis((long)(durationSeconds * 1000)));
    }

    /**
     * Record a failed RCA analysis
     *
     * @param reason Failure reason (e.g., "llm_error", "data_unavailable")
     * @param analysisType Type of analysis
     * @param namespace Kubernetes namespace
     */
    public void recordFailed(String reason, String analysisType, String namespace) {
        // Increment general count (even failures count as analyses)
        analysisCountCounter.increment();

        // Record failure with tags (matches failure monitor query)
        Counter.builder("causa_rca_analysis_failed")
                .description("Total number of failed RCA analyses")
                .tag("app", "causa")
                .tag("reason", reason != null ? reason : "unknown")
                .tag("analysis_type", analysisType != null ? analysisType : "unknown")
                .tag("namespace", namespace != null ? namespace : "unknown")
                .register(registry)
                .increment();
    }

    /**
     * Record a completed RCA analysis (simple version without duration)
     */
    public void recordCompleted() {
        recordCompleted("default", "default", 0.0);
    }

    /**
     * Record a failed RCA analysis (simple version)
     */
    public void recordFailed() {
        recordFailed("unknown", "default", "default");
    }

    /**
     * Record RCA analysis duration
     * @param duration the duration of the analysis
     */
    public void recordDuration(Duration duration) {
        recordCompleted("default", "default", duration.toMillis() / 1000.0);
    }

    /**
     * Get completed count (for testing)
     */
    public double getCompletedCount() {
        return analysisCompletedCounter.count();
    }

    /**
     * Get analysis count (for testing)
     */
    public double getAnalysisCount() {
        return analysisCountCounter.count();
    }
}
