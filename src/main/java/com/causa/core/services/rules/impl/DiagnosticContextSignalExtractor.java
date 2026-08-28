package com.causa.core.services.rules.impl;

import com.causa.common.constants.ValidationConstants.ContextKeywords;
import com.causa.common.constants.ValidationConstants.SignalMetadata;
import com.causa.common.constants.ValidationConstants.SignalNames;
import com.causa.common.constants.ValidationConstants.SignalThresholds;
import com.causa.common.constants.ValidationConstants.SignalValues;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
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

    // Quarkus metrics patterns (handles both quoted and unquoted Prometheus label values)
    private static final Pattern HEAP_AFTER_GC_PATTERN = Pattern.compile(
        "jvm_memory_usage_after_gc\\{[^}]*pool=\"?long-lived\"?[^}]*}\\s*[\":]*(\\d+\\.?\\d*E?\\d*)"
    );
    private static final Pattern HEAP_USED_BYTES_PATTERN = Pattern.compile(
        "jvm_memory_used_bytes\\{[^}]*area=\"?heap\"?[^}]*id=\"?(?:Tenured Gen|G1 Old Gen)\"?[^}]*}\\s*[\":]*(\\d+\\.?\\d*E?\\d*)"
    );
    private static final Pattern HEAP_MAX_BYTES_PATTERN = Pattern.compile(
        "jvm_memory_max_bytes\\{[^}]*area=\"?heap\"?[^}]*id=\"?(?:Tenured Gen|G1 Old Gen)\"?[^}]*}\\s*[\":]*(\\d+\\.?\\d*E?\\d*)"
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

    // GC log detail pattern: "303M->220M(365M) 53.562ms"
    private static final Pattern GC_PAUSE_DETAIL_PATTERN = Pattern.compile(
        "(\\d+)M->(\\d+)M\\((\\d+)M\\)\\s+(\\d+\\.?\\d*)ms"
    );

    // Kruize patterns
    private static final Pattern KRUIZE_MEMORY_REC_PATTERN = Pattern.compile(
        "kruize.*recommends?.*memory.*limit.*to\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<Signal> extractSignals(String diagnosticContext) {
        if (diagnosticContext == null || diagnosticContext.isBlank()) {
            log.warn(LogMessages.Validation.EMPTY_DIAGNOSTIC_CONTEXT).log();
            return List.of();
        }

        List<Signal> signals = new ArrayList<>();
        signals.addAll(extractKubernetesEventSignals(diagnosticContext));
        signals.addAll(extractContainerStatusSignals(diagnosticContext));
        signals.addAll(extractPodStatusSignals(diagnosticContext));
        signals.addAll(extractMetricSignals(diagnosticContext));
        signals.addAll(extractLogPatternSignals(diagnosticContext));
        signals.addAll(extractKruizeSignals(diagnosticContext));

        log.info(LogMessages.Validation.SIGNALS_EXTRACTED)
            .field("signalCount", signals.size())
            .log();

        return signals;
    }

    private List<Signal> extractKubernetesEventSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        Matcher reasonMatcher = REASON_PATTERN.matcher(context);
        while (reasonMatcher.find()) {
            String reason = reasonMatcher.group(1);
            signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, SignalNames.REASON)
                .value(reason)
                .build());

            if (SignalValues.OOM_KILLED.equalsIgnoreCase(reason)) {
                signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, SignalNames.TERMINATION_REASON)
                    .value(SignalValues.OOM_KILLED)
                    .build());
            }
        }

        if (context.toLowerCase().contains(ContextKeywords.ROLLOUT) ||
            context.toLowerCase().contains(ContextKeywords.DEPLOYMENT)) {
            signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, SignalNames.DEPLOYMENT)
                .value(SignalValues.DEPLOYMENT_ROLLOUT_DETECTED)
                .build());
        }

        if (context.toLowerCase().contains(ContextKeywords.EVICT)) {
            String evictionType = context.toLowerCase().contains(ContextKeywords.DISK)
                ? SignalValues.DISK_PRESSURE : SignalValues.MEMORY_PRESSURE;
            signals.add(Signal.builder(Signal.SignalType.KUBERNETES_EVENT, SignalNames.EVICTION)
                .value(SignalValues.EVICTION_PREFIX + evictionType)
                .build());
        }

        return signals;
    }

    private List<Signal> extractContainerStatusSignals(String context) {
        List<Signal> signals = new ArrayList<>();
        boolean hasExitCode137 = false;

        Matcher exitCodeMatcher = EXIT_CODE_PATTERN.matcher(context);
        while (exitCodeMatcher.find()) {
            int exitCode = Integer.parseInt(exitCodeMatcher.group(1));
            signals.add(Signal.builder(Signal.SignalType.CONTAINER_STATUS, SignalNames.EXIT_CODE)
                .value(exitCode)
                .build());
            if (exitCode == SignalThresholds.EXIT_CODE_OOM_KILLED) {
                hasExitCode137 = true;
            }
        }

        if (context.contains(ContextKeywords.OOM_KILLED)) {
            signals.add(Signal.builder(Signal.SignalType.CONTAINER_STATUS, SignalNames.TERMINATION_REASON)
                .value(SignalValues.OOM_KILLED)
                .build());
        } else if (hasExitCode137) {
            // K8s may report reason as "Error" instead of "OOMKilled" depending on
            // which crash cycle lastState captured. Exit code 137 = SIGKILL from
            // kernel OOM killer in container environments.
            signals.add(Signal.builder(Signal.SignalType.CONTAINER_STATUS, SignalNames.TERMINATION_REASON)
                .value(SignalValues.OOM_KILLED)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_EXIT_CODE_137)
                .build());
        }

        return signals;
    }

    private List<Signal> extractPodStatusSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        Matcher statusMatcher = POD_STATUS_PATTERN.matcher(context);
        while (statusMatcher.find()) {
            String status = statusMatcher.group(1);
            signals.add(Signal.builder(Signal.SignalType.POD_STATUS, SignalNames.STATUS)
                .value(status)
                .build());
        }

        if (context.contains(SignalValues.CRASH_LOOP_BACK_OFF) ||
            context.contains(ContextKeywords.BACK_OFF_RESTARTING)) {
            signals.add(Signal.builder(Signal.SignalType.POD_STATUS, SignalNames.POD_STATE)
                .value(SignalValues.CRASH_LOOP_BACK_OFF)
                .build());
        }

        return signals;
    }

    private List<Signal> extractMetricSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        Matcher memoryTrendMatcher = MEMORY_TREND_PATTERN.matcher(context);
        while (memoryTrendMatcher.find()) {
            String trend = memoryTrendMatcher.group(1).toUpperCase();
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND)
                .value(trend)
                .build());
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND)
                .value(trend)
                .build());
        }

        Matcher heapUsageMatcher = HEAP_USAGE_PATTERN.matcher(context);
        while (heapUsageMatcher.find()) {
            double heapUsage = Double.parseDouble(heapUsageMatcher.group(1));
            if (heapUsage > SignalThresholds.HEAP_USAGE_MAX_RATIO) {
                heapUsage = heapUsage / SignalThresholds.PERCENTAGE_DIVISOR;
            }
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE)
                .value(heapUsage)
                .build());
        }

        Matcher afterGcMatcher = HEAP_AFTER_GC_PATTERN.matcher(context);
        if (afterGcMatcher.find()) {
            double afterGc = Double.parseDouble(afterGcMatcher.group(1));
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE)
                .value(afterGc)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_QUARKUS_AFTER_GC)
                .build());
        }

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
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND)
                .value(SignalValues.INCREASING)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_REMAINING_LOGS)
                .build());
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND)
                .value(SignalValues.INCREASING)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_REMAINING_LOGS)
                .build());
        }

        boolean hasOomEvidence = context.contains(ContextKeywords.OOM_KILLED);
        if (!hasOomEvidence) {
            Matcher exitCheck = EXIT_CODE_PATTERN.matcher(context);
            while (exitCheck.find()) {
                if (Integer.parseInt(exitCheck.group(1)) == SignalThresholds.EXIT_CODE_OOM_KILLED) {
                    hasOomEvidence = true;
                    break;
                }
            }
        }
        if (hasOomEvidence) {
            Matcher restartCheck = RESTART_COUNT_PATTERN.matcher(context);
            if (restartCheck.find() && Integer.parseInt(restartCheck.group(1)) > 0) {
                signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND)
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_OOMKILLED_RESTARTS)
                    .build());
                signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND)
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_OOMKILLED_RESTARTS)
                    .build());
            }
        }

        Matcher restartMatcher = RESTART_COUNT_PATTERN.matcher(context);
        if (restartMatcher.find()) {
            int restartCount = Integer.parseInt(restartMatcher.group(1));
            if (restartCount > 0) {
                signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.MEMORY_PRESSURE_DURATION)
                    .value(restartCount * SignalThresholds.RESTART_TO_SECONDS_MULTIPLIER)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_RESTART_COUNT)
                    .build());
            }
        }

        Matcher usedMatcher = HEAP_USED_BYTES_PATTERN.matcher(context);
        Matcher maxMatcher = HEAP_MAX_BYTES_PATTERN.matcher(context);
        if (usedMatcher.find() && maxMatcher.find()) {
            double used = Double.parseDouble(usedMatcher.group(1));
            double max = Double.parseDouble(maxMatcher.group(1));
            if (max > 0) {
                double usagePercent = used / max;
                signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.MEMORY_USAGE_PERCENT)
                    .value(usagePercent)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_QUARKUS_MEMORY)
                    .build());
            }
        }

        return signals;
    }

    private List<Signal> extractLogPatternSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        Matcher oomErrorMatcher = OOM_ERROR_PATTERN.matcher(context);
        if (oomErrorMatcher.find()) {
            signals.add(Signal.builder(Signal.SignalType.LOG_PATTERN, SignalNames.ERROR_OOM)
                .value(oomErrorMatcher.group(0))
                .build());
        }

        Matcher fullGcMatcher = FULL_GC_PATTERN.matcher(context);
        int fullGcCount = 0;
        while (fullGcMatcher.find()) {
            fullGcCount++;
        }
        if (fullGcCount > 0) {
            signals.add(Signal.builder(Signal.SignalType.LOG_PATTERN, SignalNames.FULL_GC_COUNT)
                .value(fullGcCount)
                .metadata(SignalMetadata.FREQUENT, fullGcCount > SignalThresholds.FULL_GC_FREQUENT_THRESHOLD)
                .build());
        }

        Matcher gcDetailMatcher = GC_PAUSE_DETAIL_PATTERN.matcher(context);
        double maxPause = 0;
        double totalPause = 0;
        double maxHeapAfterGcRatio = 0;
        int gcDetailCount = 0;
        List<Double> beforeGcValues = new ArrayList<>();
        while (gcDetailMatcher.find()) {
            gcDetailCount++;
            double beforeGc = Double.parseDouble(gcDetailMatcher.group(1));
            double afterGc = Double.parseDouble(gcDetailMatcher.group(2));
            double maxHeap = Double.parseDouble(gcDetailMatcher.group(3));
            double pauseMs = Double.parseDouble(gcDetailMatcher.group(4));
            beforeGcValues.add(beforeGc);
            totalPause += pauseMs;
            if (pauseMs > maxPause) {
                maxPause = pauseMs;
            }
            if (maxHeap > 0) {
                double ratio = afterGc / maxHeap;
                if (ratio > maxHeapAfterGcRatio) {
                    maxHeapAfterGcRatio = ratio;
                }
            }
        }
        if (gcDetailCount > 0) {
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.GC_PAUSE_MAX)
                .value(maxPause)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_VERBOSE_GC)
                .build());
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.GC_PAUSE_TOTAL)
                .value(totalPause)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_VERBOSE_GC)
                .build());
            signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.HEAP_AFTER_GC_RATIO)
                .value(maxHeapAfterGcRatio)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_VERBOSE_GC)
                .build());
        }

        if (beforeGcValues.size() >= SignalThresholds.MIN_GC_VALUES_FOR_TREND) {
            int rises = 0;
            for (int i = 1; i < beforeGcValues.size(); i++) {
                if (beforeGcValues.get(i) > beforeGcValues.get(i - 1)) {
                    rises++;
                }
            }
            double riseRatio = (double) rises / (beforeGcValues.size() - 1);
            if (riseRatio >= SignalThresholds.GC_RISE_RATIO_THRESHOLD) {
                signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND)
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_GC_LOGS)
                    .build());
                signals.add(Signal.builder(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND)
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_GC_LOGS)
                    .build());
            }
        }

        return signals;
    }

    private List<Signal> extractKruizeSignals(String context) {
        List<Signal> signals = new ArrayList<>();

        Matcher kruizeMatcher = KRUIZE_MEMORY_REC_PATTERN.matcher(context);
        if (kruizeMatcher.find()) {
            String recommendation = kruizeMatcher.group(0);
            signals.add(Signal.builder(Signal.SignalType.KRUIZE_RECOMMENDATION, SignalNames.MEMORY_LIMIT_RECOMMENDATION)
                .value(SignalValues.INCREASE_MEMORY_LIMIT)
                .metadata(SignalMetadata.RECOMMENDATION, recommendation)
                .build());
        }

        return signals;
    }
}
