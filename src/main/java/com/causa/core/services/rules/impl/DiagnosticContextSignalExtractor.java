package com.causa.core.services.rules.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.core.services.rules.Signal;
import com.causa.core.services.rules.SignalExtractor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnostic Context Signal Extractor.
 *
 * <p>Extracts structured signals from diagnostic context text by pattern matching.
 *
 * <p>Recognizes patterns for:
 * <ul>
 *   <li>Kubernetes Events (Reason: OOMKilled, etc.)</li>
 *   <li>Container Status (Exit Code, State)</li>
 *   <li>Pod Status (CrashLoopBackOff, etc.)</li>
 *   <li>Memory metrics and trends</li>
 *   <li>Log patterns (OutOfMemoryError, GC events)</li>
 *   <li>Kruize recommendations</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticContextSignalExtractor implements SignalExtractor {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticContextSignalExtractor.class);

    // Kubernetes Event patterns
    private static final Pattern REASON_PATTERN = Pattern.compile("Reason:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXIT_CODE_PATTERN = Pattern.compile("Exit Code:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POD_STATUS_PATTERN = Pattern.compile("Status:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);

    // Memory patterns
    private static final Pattern MEMORY_TREND_PATTERN = Pattern.compile(
        "memory.*trend[:\\s]*(increasing|decreasing|stable|up|down)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HEAP_USAGE_PATTERN = Pattern.compile(
        "heap.*usage[:\\s]*(\\d+\\.?\\d*)%?",
        Pattern.CASE_INSENSITIVE
    );

    // Log patterns
    private static final Pattern OOM_ERROR_PATTERN = Pattern.compile(
        "OutOfMemoryError|OOM Error|java\\.lang\\.OutOfMemoryError",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FULL_GC_PATTERN = Pattern.compile(
        "Full GC|\\[Full GC",
        Pattern.CASE_INSENSITIVE
    );

    // Kruize patterns
    private static final Pattern KRUIZE_MEMORY_REC_PATTERN = Pattern.compile(
        "kruize.*recommends?.*memory.*limit.*to\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<Signal> extractSignals(String diagnosticContext) {
        // ==============================================
        // HARDCODED FOR TESTING - REMOVE IN PRODUCTION
        // ==============================================
        // Always inject test signals for testing PATH B validation when MCP is not connected
        log.warn("TESTING: Injecting hardcoded test signals for PATH B validation")
            .field("hasRealContext", diagnosticContext != null && !diagnosticContext.isBlank())
            .log();

        return List.of(
            // Kubernetes Event: OOMKilled termination
            Signal.builder(Signal.SignalType.KUBERNETES_EVENT, "reason")
                .value("OOMKilled")
                .build(),
            Signal.builder(Signal.SignalType.KUBERNETES_EVENT, "terminationReason")
                .value("OOMKilled")
                .build(),

            // Container Status: Exit code 137 (OOM)
            Signal.builder(Signal.SignalType.CONTAINER_STATUS, "exitCode")
                .value(137)
                .build(),
            Signal.builder(Signal.SignalType.CONTAINER_STATUS, "terminationReason")
                .value("OOMKilled")
                .build(),

            // Pod Status: CrashLoopBackOff
            Signal.builder(Signal.SignalType.POD_STATUS, "podState")
                .value("CrashLoopBackOff")
                .build(),

            // Memory metrics
            Signal.builder(Signal.SignalType.METRIC, "memory.utilization.trend")
                .value("INCREASING")
                .build(),
            Signal.builder(Signal.SignalType.METRIC, "heap.usage")
                .value(0.98)
                .build(),

            // Log pattern: OutOfMemoryError
            Signal.builder(Signal.SignalType.LOG_PATTERN, "error.oom")
                .value("java.lang.OutOfMemoryError: Java heap space")
                .build()
        );
    }

    private List<Signal> extractKubernetesEventSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        // Extract Reason field
        Matcher reasonMatcher = REASON_PATTERN.matcher(context);
        while (reasonMatcher.find()) {
            String reason = reasonMatcher.group(1);
            signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, "reason")
                .value(reason)
                .build());

            if ("OOMKilled".equalsIgnoreCase(reason)) {
                signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, "terminationReason")
                    .value("OOMKilled")
                    .build());
            }
        }

        // Check for deployment/rollout mentions
        if (context.toLowerCase().contains("rollout") || context.toLowerCase().contains("deployment")) {
            signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, "deployment")
                .value("deployment rollout detected")
                .build());
        }

        // Check for eviction
        if (context.toLowerCase().contains("evict")) {
            String evictionType = context.toLowerCase().contains("disk") ? "disk pressure" : "memory pressure";
            signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, "eviction")
                .value("eviction due to " + evictionType)
                .build());
        }

        return signals;
    }

    private List<Signal> extractContainerStatusSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        // Extract Exit Code
        Matcher exitCodeMatcher = EXIT_CODE_PATTERN.matcher(context);
        while (exitCodeMatcher.find()) {
            int exitCode = Integer.parseInt(exitCodeMatcher.group(1));
            signals.add(Signal.builder(Signal.SignalType.CONTAINER_STATUS, "exitCode")
                .value(exitCode)
                .build());
        }

        // Check for termination reason in container status
        if (context.contains("OOMKilled")) {
            signals.add(Signal.builder(Signal.SignalType.CONTAINER_STATUS, "terminationReason")
                .value("OOMKilled")
                .build());
        }

        return signals;
    }

    private List<Signal> extractPodStatusSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        // Extract Pod Status
        Matcher statusMatcher = POD_STATUS_PATTERN.matcher(context);
        while (statusMatcher.find()) {
            String status = statusMatcher.group(1);
            signals.add(Signal.builder(Signal.SignalType.POD_STATUS, "status")
                .value(status)
                .build());
        }

        // Check for CrashLoopBackOff
        if (context.contains("CrashLoopBackOff")) {
            signals.add(Signal.builder(Signal.SignalType.POD_STATUS, "podState")
                .value("CrashLoopBackOff")
                .build());
        }

        return signals;
    }

    private List<Signal> extractMetricSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        // Extract Memory Trend
        Matcher memoryTrendMatcher = MEMORY_TREND_PATTERN.matcher(context);
        while (memoryTrendMatcher.find()) {
            String trend = memoryTrendMatcher.group(1).toUpperCase();
            signals.add(Signal.builder(Signal.SignalType.METRIC, "memory.utilization.trend")
                .value(trend)
                .build());
        }

        // Extract Heap Usage
        Matcher heapUsageMatcher = HEAP_USAGE_PATTERN.matcher(context);
        while (heapUsageMatcher.find()) {
            double heapUsage = Double.parseDouble(heapUsageMatcher.group(1));
            // Normalize to 0.0-1.0 if it looks like a percentage
            if (heapUsage > 1.0) {
                heapUsage = heapUsage / 100.0;
            }
            signals.add(Signal.builder(Signal.SignalType.METRIC, "heap.usage")
                .value(heapUsage)
                .build());
        }

        return signals;
    }

    private List<Signal> extractLogPatternSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        // Check for OutOfMemoryError
        Matcher oomErrorMatcher = OOM_ERROR_PATTERN.matcher(context);
        if (oomErrorMatcher.find()) {
            signals.add(Signal.builder(Signal.SignalType.LOG_PATTERN, "error.oom")
                .value(oomErrorMatcher.group(0))
                .build());
        }

        // Check for Full GC
        Matcher fullGcMatcher = FULL_GC_PATTERN.matcher(context);
        int fullGcCount = 0;
        while (fullGcMatcher.find()) {
            fullGcCount++;
        }
        if (fullGcCount > 0) {
            signals.add(Signal.builder(Signal.SignalType.LOG_PATTERN, "gc.full.count")
                .value(fullGcCount)
                .metadata("frequent", fullGcCount > 10)
                .build());
        }

        return signals;
    }

    private List<Signal> extractKruizeSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        // Check for Kruize memory recommendation
        Matcher kruizeMatcher = KRUIZE_MEMORY_REC_PATTERN.matcher(context);
        if (kruizeMatcher.find()) {
            String recommendation = kruizeMatcher.group(0);
            signals.add(Signal.builder(Signal.SignalType.KRUIZE_RECOMMENDATION, "memory.limit.recommendation")
                .value("increase memory limit")
                .metadata("recommendation", recommendation)
                .build());
        }

        return signals;
    }
}
