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

    // Quarkus metrics patterns
    private static final Pattern HEAP_AFTER_GC_PATTERN = Pattern.compile(
        "jvm_memory_usage_after_gc\\{[^}]*pool=long-lived[^}]*}[\":]*(\\d+\\.\\d+)"
    );
    private static final Pattern RESTART_COUNT_PATTERN = Pattern.compile(
        "Restart Count:\\s*(\\d+)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REMAINING_PATTERN = Pattern.compile(
        "remaining=(\\d+)B.*max=(\\d+)B"
    );

    // Log patterns
    private static final Pattern OOM_ERROR_PATTERN = Pattern.compile(
        "OutOfMemoryError|OOM Error|java\\.lang\\.OutOfMemoryError",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FULL_GC_PATTERN = Pattern.compile(
        "Pause Full|Full GC|\\[Full GC",
        Pattern.CASE_INSENSITIVE
    );

    // Kruize patterns
    private static final Pattern KRUIZE_MEMORY_REC_PATTERN = Pattern.compile(
        "kruize.*recommends?.*memory.*limit.*to\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<Signal> extractSignals(String diagnosticContext) {
        if (diagnosticContext == null || diagnosticContext.isBlank()) {
            log.warn("Empty diagnostic context, no signals to extract").log();
            return List.of();
        }

        List<Signal> signals = new ArrayList<>();
        signals.addAll(extractKubernetesEventSignals(diagnosticContext));
        signals.addAll(extractContainerStatusSignals(diagnosticContext));
        signals.addAll(extractPodStatusSignals(diagnosticContext));
        signals.addAll(extractMetricSignals(diagnosticContext));
        signals.addAll(extractLogPatternSignals(diagnosticContext));
        signals.addAll(extractKruizeSignals(diagnosticContext));

        log.info("Extracted signals from diagnostic context")
            .field("signalCount", signals.size())
            .log();

        return signals;
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

        // Extract Memory Trend from explicit text
        Matcher memoryTrendMatcher = MEMORY_TREND_PATTERN.matcher(context);
        while (memoryTrendMatcher.find()) {
            String trend = memoryTrendMatcher.group(1).toUpperCase();
            signals.add(Signal.builder(Signal.SignalType.METRIC, "memory.utilization.trend")
                .value(trend)
                .build());
            signals.add(Signal.builder(Signal.SignalType.METRIC, "heap.usage.trend")
                .value(trend)
                .build());
        }

        // Extract Heap Usage from explicit text
        Matcher heapUsageMatcher = HEAP_USAGE_PATTERN.matcher(context);
        while (heapUsageMatcher.find()) {
            double heapUsage = Double.parseDouble(heapUsageMatcher.group(1));
            if (heapUsage > 1.0) {
                heapUsage = heapUsage / 100.0;
            }
            signals.add(Signal.builder(Signal.SignalType.METRIC, "heap.usage")
                .value(heapUsage)
                .build());
        }

        // Derive heap usage from Quarkus jvm_memory_usage_after_gc metric
        Matcher afterGcMatcher = HEAP_AFTER_GC_PATTERN.matcher(context);
        if (afterGcMatcher.find()) {
            double afterGc = Double.parseDouble(afterGcMatcher.group(1));
            signals.add(Signal.builder(Signal.SignalType.METRIC, "heap.usage")
                .value(afterGc)
                .metadata("source", "quarkus_jvm_memory_usage_after_gc")
                .build());
        }

        // Derive memory trend from 'remaining' values in allocation logs
        Matcher remainingMatcher = REMAINING_PATTERN.matcher(context);
        long prevRemaining = -1;
        boolean increasing = false;
        while (remainingMatcher.find()) {
            long remaining = Long.parseLong(remainingMatcher.group(1));
            if (prevRemaining > 0 && remaining < prevRemaining) {
                increasing = true;
            }
            prevRemaining = remaining;
        }
        if (increasing) {
            signals.add(Signal.builder(Signal.SignalType.METRIC, "memory.utilization.trend")
                .value("INCREASING")
                .metadata("source", "derived_from_remaining_logs")
                .build());
            signals.add(Signal.builder(Signal.SignalType.METRIC, "heap.usage.trend")
                .value("INCREASING")
                .metadata("source", "derived_from_remaining_logs")
                .build());
        }

        // Derive memory pressure duration from restart count
        Matcher restartMatcher = RESTART_COUNT_PATTERN.matcher(context);
        if (restartMatcher.find()) {
            int restartCount = Integer.parseInt(restartMatcher.group(1));
            if (restartCount > 0) {
                signals.add(Signal.builder(Signal.SignalType.METRIC, "memory.pressure.duration")
                    .value(restartCount * 300)
                    .metadata("source", "derived_from_restart_count")
                    .build());
            }
        }

        // Derive memory usage percent from Quarkus metrics
        Matcher usedMatcher = Pattern.compile(
            "jvm_memory_used_bytes\\{area=heap,id=Tenured Gen}[\":]*(\\d+\\.?\\d*E?\\d*)"
        ).matcher(context);
        Matcher maxMatcher = Pattern.compile(
            "jvm_memory_max_bytes\\{area=heap,id=Tenured Gen}[\":]*(\\d+\\.?\\d*E?\\d*)"
        ).matcher(context);
        if (usedMatcher.find() && maxMatcher.find()) {
            double used = Double.parseDouble(usedMatcher.group(1));
            double max = Double.parseDouble(maxMatcher.group(1));
            if (max > 0) {
                double usagePercent = used / max;
                signals.add(Signal.builder(Signal.SignalType.METRIC, "memory.usage.percent")
                    .value(usagePercent)
                    .metadata("source", "quarkus_jvm_memory")
                    .build());
            }
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
            signals.add(Signal.builder(Signal.SignalType.LOG_PATTERN, "full.gc.count")
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
