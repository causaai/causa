package com.causa.core.services.rules.impl;

import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.common.constants.ValidationConstants.ContextKeywords;
import com.causa.common.constants.ValidationConstants.SignalMetadata;
import com.causa.common.constants.ValidationConstants.SignalNames;
import com.causa.common.constants.ValidationConstants.SignalThresholds;
import com.causa.common.constants.ValidationConstants.SignalValues;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.DiagnosticContextIndex;
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
 * <p>Every emitted signal carries the diagnostic context section it was read from, under
 * {@link Metadata#SIGNAL_SECTION}. Downstream evidence mapping uses that section to attribute
 * the signal to the MCP server that produced it, so a signal without a section cannot be
 * traced back to a source.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DiagnosticContextSignalExtractor implements SignalExtractor {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticContextSignalExtractor.class);

    // Kubernetes Event patterns
    // "Reason:" is anchored to the start of a line because it is a status field, not prose.
    // Unanchored it also matched narrative text such as the autoscaler's
    // "New size: 6; reason: cpu resource utilization", yielding a bogus reason=cpu signal.
    private static final Pattern REASON_PATTERN = Pattern.compile(
        "^\\s*Reason:\\s*(\\w+)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    private static final Pattern EXIT_CODE_PATTERN = Pattern.compile("Exit Code:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POD_STATUS_PATTERN = Pattern.compile("Status:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESTART_COUNT_PATTERN = Pattern.compile("Restart Count:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

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

    // GC log detail pattern: supports decimal values and K/M/G units
    // Examples: "303M->220M(365M) 53.562ms", "512.5M->256.2M(365.8M) 42.1ms", "1.5G->900M(2G) 120ms"
    private static final Pattern GC_PAUSE_DETAIL_PATTERN = Pattern.compile(
        "(\\d+\\.?\\d*)([KMG])->(\\d+\\.?\\d*)([KMG])\\((\\d+\\.?\\d*)([KMG])\\)\\s+(\\d+\\.?\\d*)ms",
        Pattern.CASE_INSENSITIVE
    );

    // Quarkus GC pause metrics patterns (JSON format from MCP metrics endpoint)
    private static final Pattern QUARKUS_GC_PAUSE_SUM_PATTERN = Pattern.compile(
        "\"jvm_gc_pause_seconds_sum\\{[^}]*}\"\\s*:\\s*(\\d+\\.?\\d*(?:E-?\\d+)?)"
    );
    private static final Pattern QUARKUS_PROCESS_UPTIME_PATTERN = Pattern.compile(
        "\"process_uptime_seconds\"\\s*:\\s*(\\d+\\.?\\d*)"
    );

    // Kruize patterns
    private static final Pattern KRUIZE_MEMORY_REC_PATTERN = Pattern.compile(
        "kruize.*recommends?.*memory.*limit.*to\\s*(\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Converts JVM memory size to megabytes.
     *
     * @param value The numeric value
     * @param unit  The unit (K, M, or G - case insensitive)
     * @return The value in megabytes
     */
    private static double convertToMegabytes(double value, String unit) {
        return switch (unit.toUpperCase()) {
            case "K" -> value / 1024.0;
            case "M" -> value;
            case "G" -> value * 1024.0;
            default -> value; // Default to megabytes if unit is unknown
        };
    }

    @Override
    public List<Signal> extractSignals(String diagnosticContext) {
        if (diagnosticContext == null || diagnosticContext.isBlank()) {
            log.warn(LogMessages.Validation.EMPTY_DIAGNOSTIC_CONTEXT).log();
            return List.of();
        }

        DiagnosticContextIndex sections = DiagnosticContextIndex.of(diagnosticContext);

        List<Signal> signals = new ArrayList<>();
        signals.addAll(extractKubernetesEventSignals(diagnosticContext, sections));
        signals.addAll(extractContainerStatusSignals(diagnosticContext, sections));
        signals.addAll(extractPodStatusSignals(diagnosticContext, sections));
        signals.addAll(extractMetricSignals(diagnosticContext, sections));
        signals.addAll(extractLogPatternSignals(diagnosticContext, sections));
        signals.addAll(extractQuarkusGcPauseSignals(diagnosticContext, sections));
        signals.addAll(extractKruizeSignals(diagnosticContext, sections));

        log.info(LogMessages.Validation.SIGNALS_EXTRACTED)
            .field("signalCount", signals.size())
            .log();

        return signals;
    }

    /**
     * Starts a signal already attributed to the context section the match came from, carrying
     * the verbatim context line it was read from.
     *
     * <p>An offset of -1, or an offset landing in the context preamble, leaves the section
     * unset — the signal is still valid, it simply cannot be attributed.
     */
    private static Signal.Builder signal(Signal.SignalType type, String name,
                                         DiagnosticContextIndex sections, int offset) {
        Signal.Builder builder = Signal.builder(type, name);
        String section = sections.labelAt(offset);
        if (section != null) {
            builder.metadata(Metadata.SIGNAL_SECTION, section);
        }
        String line = sections.lineAt(offset);
        if (line != null) {
            builder.metadata(Metadata.SIGNAL_SNIPPET, line);
        }
        String window = sections.contextAt(offset);
        if (window != null && !window.equals(line)) {
            builder.metadata(Metadata.SIGNAL_CONTEXT, window);
        }
        return builder;
    }

    /** Offset of {@code needle} in {@code haystack}, or -1 — {@link String#indexOf} by another name. */
    private static int offsetOf(String haystack, String needle) {
        return haystack.indexOf(needle);
    }

    private List<Signal> extractKubernetesEventSignals(String context, DiagnosticContextIndex sections) {
        List<Signal> signals = new ArrayList<>();

        Matcher reasonMatcher = REASON_PATTERN.matcher(context);
        while (reasonMatcher.find()) {
            String reason = reasonMatcher.group(1);
            signals.add(signal(Signal.SignalType.KUBERNETES_EVENT, SignalNames.REASON, sections, reasonMatcher.start())
                .value(reason)
                .build());

            if (SignalValues.OOM_KILLED.equalsIgnoreCase(reason)) {
                signals.add(signal(Signal.SignalType.KUBERNETES_EVENT, SignalNames.TERMINATION_REASON, sections, reasonMatcher.start())
                    .value(SignalValues.OOM_KILLED)
                    .build());
            }
        }

        String lower = context.toLowerCase();

        int rolloutAt = offsetOf(lower, ContextKeywords.ROLLOUT);
        int deploymentAt = offsetOf(lower, ContextKeywords.DEPLOYMENT);
        if (rolloutAt >= 0 || deploymentAt >= 0) {
            signals.add(signal(Signal.SignalType.KUBERNETES_EVENT, SignalNames.DEPLOYMENT,
                    sections, rolloutAt >= 0 ? rolloutAt : deploymentAt)
                .value(SignalValues.DEPLOYMENT_ROLLOUT_DETECTED)
                .build());
        }

        int evictAt = offsetOf(lower, ContextKeywords.EVICT);
        if (evictAt >= 0) {
            String evictionType = lower.contains(ContextKeywords.DISK)
                ? SignalValues.DISK_PRESSURE : SignalValues.MEMORY_PRESSURE;
            signals.add(signal(Signal.SignalType.KUBERNETES_EVENT, SignalNames.EVICTION, sections, evictAt)
                .value(SignalValues.EVICTION_PREFIX + evictionType)
                .build());
        }

        return signals;
    }

    private List<Signal> extractContainerStatusSignals(String context, DiagnosticContextIndex sections) {
        List<Signal> signals = new ArrayList<>();
        int exitCode137At = -1;

        Matcher exitCodeMatcher = EXIT_CODE_PATTERN.matcher(context);
        while (exitCodeMatcher.find()) {
            int exitCode = Integer.parseInt(exitCodeMatcher.group(1));
            signals.add(signal(Signal.SignalType.CONTAINER_STATUS, SignalNames.EXIT_CODE, sections, exitCodeMatcher.start())
                .value(exitCode)
                .build());
            if (exitCode == SignalThresholds.EXIT_CODE_OOM_KILLED && exitCode137At < 0) {
                exitCode137At = exitCodeMatcher.start();
            }
        }

        int oomKilledAt = offsetOf(context.toLowerCase(), ContextKeywords.OOM_KILLED.toLowerCase());
        if (oomKilledAt >= 0) {
            signals.add(signal(Signal.SignalType.CONTAINER_STATUS, SignalNames.TERMINATION_REASON, sections, oomKilledAt)
                .value(SignalValues.OOM_KILLED)
                .build());
        } else if (exitCode137At >= 0) {
            // K8s may report reason as "Error" instead of "OOMKilled" depending on
            // which crash cycle lastState captured. Exit code 137 = SIGKILL from
            // kernel OOM killer in container environments.
            signals.add(signal(Signal.SignalType.CONTAINER_STATUS, SignalNames.TERMINATION_REASON, sections, exitCode137At)
                .value(SignalValues.OOM_KILLED)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_EXIT_CODE_137)
                .build());
        }

        // Extract Restart Count
        Matcher restartCountMatcher = RESTART_COUNT_PATTERN.matcher(context);
        if (restartCountMatcher.find()) {
            int restartCount = Integer.parseInt(restartCountMatcher.group(1));
            signals.add(signal(Signal.SignalType.CONTAINER_STATUS, "container.restart.count", sections, restartCountMatcher.start())
                .value(restartCount)
                .build());
        }

        return signals;
    }

    private List<Signal> extractPodStatusSignals(String context, DiagnosticContextIndex sections) {
        List<Signal> signals = new ArrayList<>();

        Matcher statusMatcher = POD_STATUS_PATTERN.matcher(context);
        while (statusMatcher.find()) {
            String status = statusMatcher.group(1);
            signals.add(signal(Signal.SignalType.POD_STATUS, SignalNames.STATUS, sections, statusMatcher.start())
                .value(status)
                .build());
        }

        String lower = context.toLowerCase();
        int crashLoopAt = offsetOf(lower, SignalValues.CRASH_LOOP_BACK_OFF.toLowerCase());
        if (crashLoopAt < 0) {
            crashLoopAt = offsetOf(lower, ContextKeywords.BACK_OFF_RESTARTING.toLowerCase());
        }
        if (crashLoopAt >= 0) {
            signals.add(signal(Signal.SignalType.POD_STATUS, SignalNames.POD_STATE, sections, crashLoopAt)
                .value(SignalValues.CRASH_LOOP_BACK_OFF)
                .build());
        }

        return signals;
    }

    private List<Signal> extractMetricSignals(String context, DiagnosticContextIndex sections) {
        List<Signal> signals = new ArrayList<>();

        Matcher memoryTrendMatcher = MEMORY_TREND_PATTERN.matcher(context);
        while (memoryTrendMatcher.find()) {
            String trend = memoryTrendMatcher.group(1).toUpperCase();
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND, sections, memoryTrendMatcher.start())
                .value(trend)
                .build());
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND, sections, memoryTrendMatcher.start())
                .value(trend)
                .build());
        }

        Matcher heapUsageMatcher = HEAP_USAGE_PATTERN.matcher(context);
        while (heapUsageMatcher.find()) {
            double heapUsage = Double.parseDouble(heapUsageMatcher.group(1));
            if (heapUsage > SignalThresholds.HEAP_USAGE_MAX_RATIO) {
                heapUsage = heapUsage / SignalThresholds.PERCENTAGE_DIVISOR;
            }
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE, sections, heapUsageMatcher.start())
                .value(heapUsage)
                .build());
        }

        Matcher afterGcMatcher = HEAP_AFTER_GC_PATTERN.matcher(context);
        if (afterGcMatcher.find()) {
            double afterGc = Double.parseDouble(afterGcMatcher.group(1));
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.HEAP_AFTER_GC_RATIO, sections, afterGcMatcher.start())
                .value(afterGc)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_QUARKUS_AFTER_GC)
                .build());
        }

        Matcher remainingMatcher = REMAINING_PATTERN.matcher(context);
        long prevRemaining = -1;
        int increasingAt = -1;
        while (remainingMatcher.find()) {
            long remaining = Long.parseLong(remainingMatcher.group(1));
            if (prevRemaining > 0 && remaining < prevRemaining && increasingAt < 0) {
                increasingAt = remainingMatcher.start();
            }
            prevRemaining = remaining;
        }
        if (increasingAt >= 0) {
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND, sections, increasingAt)
                .value(SignalValues.INCREASING)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_REMAINING_LOGS)
                .build());
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND, sections, increasingAt)
                .value(SignalValues.INCREASING)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_REMAINING_LOGS)
                .build());
        }

        boolean hasOomEvidence = context.toLowerCase().contains(ContextKeywords.OOM_KILLED.toLowerCase());
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
                signals.add(signal(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND, sections, restartCheck.start())
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_OOMKILLED_RESTARTS)
                    .build());
                signals.add(signal(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND, sections, restartCheck.start())
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_OOMKILLED_RESTARTS)
                    .build());
            }
        }

        Matcher restartMatcher = RESTART_COUNT_PATTERN.matcher(context);
        if (restartMatcher.find()) {
            int restartCount = Integer.parseInt(restartMatcher.group(1));
            if (restartCount > 0) {
                signals.add(signal(Signal.SignalType.METRIC, SignalNames.MEMORY_PRESSURE_DURATION, sections, restartMatcher.start())
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
                signals.add(signal(Signal.SignalType.METRIC, SignalNames.MEMORY_USAGE_PERCENT, sections, usedMatcher.start())
                    .value(usagePercent)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_QUARKUS_MEMORY)
                    .build());
            }
        }

        return signals;
    }

    private List<Signal> extractLogPatternSignals(String context, DiagnosticContextIndex sections) {
        List<Signal> signals = new ArrayList<>();

        Matcher oomErrorMatcher = OOM_ERROR_PATTERN.matcher(context);
        if (oomErrorMatcher.find()) {
            signals.add(signal(Signal.SignalType.LOG_PATTERN, SignalNames.ERROR_OOM, sections, oomErrorMatcher.start())
                .value(oomErrorMatcher.group(0))
                .build());
        }

        Matcher fullGcMatcher = FULL_GC_PATTERN.matcher(context);
        int fullGcCount = 0;
        int firstFullGcAt = -1;
        while (fullGcMatcher.find()) {
            if (firstFullGcAt < 0) {
                firstFullGcAt = fullGcMatcher.start();
            }
            fullGcCount++;
        }
        if (fullGcCount > 0) {
            signals.add(signal(Signal.SignalType.LOG_PATTERN, SignalNames.FULL_GC_COUNT, sections, firstFullGcAt)
                .value(fullGcCount)
                .metadata(SignalMetadata.FREQUENT, fullGcCount > SignalThresholds.FULL_GC_FREQUENT_THRESHOLD)
                .build());
        }

        Matcher gcDetailMatcher = GC_PAUSE_DETAIL_PATTERN.matcher(context);
        int firstGcDetailAt = -1;
        double maxPause = 0;
        double totalPause = 0;
        double maxHeapAfterGcRatio = 0;
        int gcDetailCount = 0;
        List<Double> beforeGcValues = new ArrayList<>();
        while (gcDetailMatcher.find()) {
            if (firstGcDetailAt < 0) {
                firstGcDetailAt = gcDetailMatcher.start();
            }
            gcDetailCount++;
            // Parse values and units, then normalize to megabytes
            double beforeGc = convertToMegabytes(
                Double.parseDouble(gcDetailMatcher.group(1)),
                gcDetailMatcher.group(2)
            );
            double afterGc = convertToMegabytes(
                Double.parseDouble(gcDetailMatcher.group(3)),
                gcDetailMatcher.group(4)
            );
            double maxHeap = convertToMegabytes(
                Double.parseDouble(gcDetailMatcher.group(5)),
                gcDetailMatcher.group(6)
            );
            double pauseMs = Double.parseDouble(gcDetailMatcher.group(7));
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
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.GC_PAUSE_MAX, sections, firstGcDetailAt)
                .value(maxPause)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_VERBOSE_GC)
                .build());
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.GC_PAUSE_TOTAL, sections, firstGcDetailAt)
                .value(totalPause)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_VERBOSE_GC)
                .build());
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.HEAP_AFTER_GC_RATIO, sections, firstGcDetailAt)
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
                signals.add(signal(Signal.SignalType.METRIC, SignalNames.MEMORY_UTILIZATION_TREND, sections, firstGcDetailAt)
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_GC_LOGS)
                    .build());
                signals.add(signal(Signal.SignalType.METRIC, SignalNames.HEAP_USAGE_TREND, sections, firstGcDetailAt)
                    .value(SignalValues.INCREASING)
                    .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_GC_LOGS)
                    .build());
            }
        }

        return signals;
    }

    private List<Signal> extractQuarkusGcPauseSignals(String context, DiagnosticContextIndex sections) {
        List<Signal> signals = new ArrayList<>();

        Matcher uptimeMatcher = QUARKUS_PROCESS_UPTIME_PATTERN.matcher(context);
        if (!uptimeMatcher.find()) {
            return signals;
        }
        double uptimeSeconds = Double.parseDouble(uptimeMatcher.group(1));
        if (uptimeSeconds <= 0) {
            return signals;
        }

        Matcher pauseSumMatcher = QUARKUS_GC_PAUSE_SUM_PATTERN.matcher(context);
        double totalPauseSeconds = 0;
        int firstPauseSumAt = -1;
        while (pauseSumMatcher.find()) {
            if (firstPauseSumAt < 0) {
                firstPauseSumAt = pauseSumMatcher.start();
            }
            totalPauseSeconds += Double.parseDouble(pauseSumMatcher.group(1));
        }

        if (totalPauseSeconds > 0) {
            double pausePercent = totalPauseSeconds / uptimeSeconds;
            signals.add(signal(Signal.SignalType.METRIC, SignalNames.GC_PAUSE_PERCENT, sections, firstPauseSumAt)
                .value(pausePercent)
                .metadata(SignalMetadata.SOURCE, SignalMetadata.SOURCE_QUARKUS_GC_PAUSE)
                .build());
        }

        return signals;
    }

    private List<Signal> extractKruizeSignals(String context, DiagnosticContextIndex sections) {
        List<Signal> signals = new ArrayList<>();

        Matcher kruizeMatcher = KRUIZE_MEMORY_REC_PATTERN.matcher(context);
        if (kruizeMatcher.find()) {
            String recommendation = kruizeMatcher.group(0);
            signals.add(signal(Signal.SignalType.KRUIZE_RECOMMENDATION, SignalNames.MEMORY_LIMIT_RECOMMENDATION, sections, kruizeMatcher.start())
                .value(SignalValues.INCREASE_MEMORY_LIMIT)
                .metadata(SignalMetadata.RECOMMENDATION, recommendation)
                .build());
        }

        return signals;
    }
}
