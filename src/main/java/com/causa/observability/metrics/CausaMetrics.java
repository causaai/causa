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
 * Exposes Prometheus metrics for Datadog monitoring:
 * - causa_rca_analysis_available: Number of RCA analyses available
 * - causa_rca_analysis_completed_total: Total completed RCA analyses
 * - causa_rca_analysis_failed_total: Total failed RCA analyses
 * - causa_rca_analysis_duration_seconds: Duration of RCA analysis
 */
@ApplicationScoped
public class CausaMetrics {

    private final Counter analysisCompletedCounter;
    private final Counter analysisFailedCounter;
    private final Timer analysisTimer;

    @Inject
    public CausaMetrics(MeterRegistry registry) {
        this.analysisCompletedCounter = Counter.builder("causa_rca_analysis_completed_total")
                .description("Total number of completed RCA analyses")
                .tag("app", "causa")
                .register(registry);

        this.analysisFailedCounter = Counter.builder("causa_rca_analysis_failed_total")
                .description("Total number of failed RCA analyses")
                .tag("app", "causa")
                .register(registry);

        this.analysisTimer = Timer.builder("causa_rca_analysis_duration_seconds")
                .description("Duration of RCA analysis in seconds")
                .tag("app", "causa")
                .register(registry);
    }

    /**
     * Record a completed RCA analysis
     */
    public void recordCompleted() {
        analysisCompletedCounter.increment();
    }

    /**
     * Record a failed RCA analysis
     */
    public void recordFailed() {
        analysisFailedCounter.increment();
    }

    /**
     * Record RCA analysis duration
     * @param duration the duration of the analysis
     */
    public void recordDuration(Duration duration) {
        analysisTimer.record(duration);
    }

    /**
     * Get completed count (for testing)
     */
    public double getCompletedCount() {
        return analysisCompletedCounter.count();
    }

    /**
     * Get failed count (for testing)
     */
    public double getFailedCount() {
        return analysisFailedCounter.count();
    }
}
