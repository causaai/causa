package com.causa.core.services.rules.oom;

import com.causa.core.services.rules.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.regex.Pattern;

/**
 * OOMKilled Rule Set.
 *
 * <p>Deterministic validation rules for OOMKilled hypothesis.
 *
 * <p>Based on Kubernetes container termination patterns:
 * <ul>
 *   <li>Exit code 137 (SIGKILL)</li>
 *   <li>Termination reason: OOMKilled</li>
 *   <li>Memory usage trends</li>
 *   <li>Heap exhaustion patterns</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class OomKilledRuleSet implements RuleSet {

    private static final String HYPOTHESIS = "OOMKilled";

    // Score thresholds
    private static final int MIN_SUPPORTED_SCORE = 10;
    private static final int MIN_PARTIALLY_SUPPORTED_SCORE = 5;

    private final List<Rule> requiredRules;
    private final List<Rule> supportingRules;
    private final List<Rule> exclusionRules;

    public OomKilledRuleSet() {
        this.requiredRules = List.of(
            new ExitCode137Rule(),
            new OomKilledReasonRule(),
            new MemoryUtilizationIncreasingRule(),
            new NoManualRestartRule()
        );

        this.supportingRules = List.of(
            new HeapUsageExceededRule(),
            new FrequentFullGcRule(),
            new IncreasingHeapTrendRule(),
            new IncreasingObjectAllocationRule(),
            new KruizeMemoryRecommendationRule(),
            new OutOfMemoryErrorInLogsRule(),
            new HighMemoryPressureEventRule()
        );

        this.exclusionRules = List.of(
            new DeploymentRolloutRestartRule(),
            new NodeEvictionDiskPressureRule(),
            new CrashLoopBackOffRule(),
            new ApplicationExitedNormallyRule()
        );
    }

    @Override
    public String getHypothesisName() {
        return HYPOTHESIS;
    }

    @Override
    public List<Rule> getRequiredRules() {
        return requiredRules;
    }

    @Override
    public List<Rule> getSupportingRules() {
        return supportingRules;
    }

    @Override
    public List<Rule> getExclusionRules() {
        return exclusionRules;
    }

    @Override
    public int getMinSupportedScore() {
        return MIN_SUPPORTED_SCORE;
    }

    @Override
    public int getMinPartiallySupportedScore() {
        return MIN_PARTIALLY_SUPPORTED_SCORE;
    }

    // ========== REQUIRED RULES ==========

    /**
     * REQUIRED: Container must have terminated with exit code 137.
     */
    private static class ExitCode137Rule extends Rule.BaseRule {
        ExitCode137Rule() {
            super(
                "oom.required.exit_code_137",
                "Container terminated with exit code 137 (SIGKILL)",
                RuleType.REQUIRED,
                1
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.CONTAINER_STATUS)
                .filter(s -> "exitCode".equals(s.getName()))
                .filter(s -> s.getValueAsInt().orElse(0) == 137)
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Found container exit code 137"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No exit code 137 found in container status"
            );
        }
    }

    /**
     * REQUIRED: Termination reason must be OOMKilled.
     */
    private static class OomKilledReasonRule extends Rule.BaseRule {
        OomKilledReasonRule() {
            super(
                "oom.required.reason_oomkilled",
                "Container termination reason is OOMKilled",
                RuleType.REQUIRED,
                1
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.KUBERNETES_EVENT ||
                             s.getType() == Signal.SignalType.CONTAINER_STATUS)
                .filter(s -> "terminationReason".equals(s.getName()) ||
                             "reason".equals(s.getName()))
                .filter(s -> "OOMKilled".equalsIgnoreCase(s.getValueAsString()))
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Found OOMKilled termination reason"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No OOMKilled termination reason found"
            );
        }
    }

    /**
     * REQUIRED: Memory utilization must show increasing trend.
     */
    private static class MemoryUtilizationIncreasingRule extends Rule.BaseRule {
        MemoryUtilizationIncreasingRule() {
            super(
                "oom.required.memory_increasing",
                "Memory utilization continuously increased",
                RuleType.REQUIRED,
                1
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.METRIC)
                .filter(s -> s.getName().contains("memory") && s.getName().contains("trend"))
                .filter(s -> "INCREASING".equalsIgnoreCase(s.getValueAsString()) ||
                             "UP".equalsIgnoreCase(s.getValueAsString()))
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Found increasing memory trend"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No increasing memory trend detected"
            );
        }
    }

    /**
     * REQUIRED: Pod must not have been restarted manually.
     */
    private static class NoManualRestartRule extends Rule.BaseRule {
        NoManualRestartRule() {
            super(
                "oom.required.no_manual_restart",
                "No manual pod restart occurred",
                RuleType.REQUIRED,
                1
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> manualRestarts = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.KUBERNETES_EVENT)
                .filter(s -> s.getName().contains("restart") || s.getName().contains("delete"))
                .filter(s -> {
                    String val = s.getValueAsString();
                    return val != null && (val.contains("manual") || val.contains("kubectl"));
                })
                .toList();

            if (manualRestarts.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    List.of(),
                    "No manual restart detected"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "Manual restart detected: " + manualRestarts.get(0).getValueAsString()
            );
        }
    }

    // ========== SUPPORTING RULES ==========

    /**
     * SUPPORTING: Heap usage exceeded threshold.
     */
    private static class HeapUsageExceededRule extends Rule.BaseRule {
        private static final double HEAP_THRESHOLD = 0.90; // 90%

        HeapUsageExceededRule() {
            super(
                "oom.supporting.heap_exceeded",
                "Heap usage exceeded 90% threshold",
                RuleType.SUPPORTING,
                5
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.METRIC ||
                             s.getType() == Signal.SignalType.JVM_ANALYSIS)
                .filter(s -> s.getName().contains("heap") && s.getName().contains("usage"))
                .filter(s -> s.getValueAsDouble().orElse(0.0) > HEAP_THRESHOLD)
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    String.format("Heap usage exceeded %.0f%%", HEAP_THRESHOLD * 100)
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "Heap usage did not exceed threshold"
            );
        }
    }

    /**
     * SUPPORTING: Frequent Full GC events.
     */
    private static class FrequentFullGcRule extends Rule.BaseRule {
        FrequentFullGcRule() {
            super(
                "oom.supporting.frequent_full_gc",
                "Frequent Full GC events detected",
                RuleType.SUPPORTING,
                3
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.JVM_ANALYSIS ||
                             s.getType() == Signal.SignalType.LOG_PATTERN)
                .filter(s -> s.getName().contains("gc") && s.getName().contains("full"))
                .filter(s -> {
                    // Check for high frequency or "frequent" tag
                    Integer count = s.getValueAsInt().orElse(0);
                    String val = s.getValueAsString();
                    return count > 10 || (val != null && val.contains("frequent"));
                })
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Frequent Full GC detected"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No frequent Full GC detected"
            );
        }
    }

    /**
     * SUPPORTING: Increasing heap trend.
     */
    private static class IncreasingHeapTrendRule extends Rule.BaseRule {
        IncreasingHeapTrendRule() {
            super(
                "oom.supporting.heap_trend_increasing",
                "Heap usage shows increasing trend",
                RuleType.SUPPORTING,
                4
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.METRIC ||
                             s.getType() == Signal.SignalType.JVM_ANALYSIS)
                .filter(s -> s.getName().contains("heap") && s.getName().contains("trend"))
                .filter(s -> "INCREASING".equalsIgnoreCase(s.getValueAsString()))
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Heap trend is increasing"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No increasing heap trend"
            );
        }
    }

    /**
     * SUPPORTING: Increasing object allocation rate.
     */
    private static class IncreasingObjectAllocationRule extends Rule.BaseRule {
        IncreasingObjectAllocationRule() {
            super(
                "oom.supporting.allocation_increasing",
                "Object allocation rate is increasing",
                RuleType.SUPPORTING,
                3
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.JVM_ANALYSIS)
                .filter(s -> s.getName().contains("allocation") && s.getName().contains("trend"))
                .filter(s -> "INCREASING".equalsIgnoreCase(s.getValueAsString()))
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Object allocation rate increasing"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No increasing allocation rate detected"
            );
        }
    }

    /**
     * SUPPORTING: Kruize recommends increasing memory.
     */
    private static class KruizeMemoryRecommendationRule extends Rule.BaseRule {
        KruizeMemoryRecommendationRule() {
            super(
                "oom.supporting.kruize_recommends_memory",
                "Kruize recommends increasing memory limits",
                RuleType.SUPPORTING,
                4
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.KRUIZE_RECOMMENDATION)
                .filter(s -> s.getName().contains("memory"))
                .filter(s -> {
                    String val = s.getValueAsString();
                    return val != null && (val.contains("increase") || val.contains("raise"));
                })
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Kruize recommends memory increase"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No Kruize memory recommendation"
            );
        }
    }

    /**
     * SUPPORTING: OutOfMemoryError in application logs.
     */
    private static class OutOfMemoryErrorInLogsRule extends Rule.BaseRule {
        private static final Pattern OOM_PATTERN = Pattern.compile(
            "OutOfMemoryError|OOM|java\\.lang\\.OutOfMemoryError",
            Pattern.CASE_INSENSITIVE
        );

        OutOfMemoryErrorInLogsRule() {
            super(
                "oom.supporting.oom_error_in_logs",
                "OutOfMemoryError found in application logs",
                RuleType.SUPPORTING,
                5
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.LOG_PATTERN)
                .filter(s -> {
                    String val = s.getValueAsString();
                    return val != null && OOM_PATTERN.matcher(val).find();
                })
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "OutOfMemoryError found in logs"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No OutOfMemoryError in logs"
            );
        }
    }

    /**
     * SUPPORTING: High memory pressure event.
     */
    private static class HighMemoryPressureEventRule extends Rule.BaseRule {
        HighMemoryPressureEventRule() {
            super(
                "oom.supporting.memory_pressure",
                "High memory pressure event detected",
                RuleType.SUPPORTING,
                2
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.KUBERNETES_EVENT)
                .filter(s -> s.getName().contains("pressure"))
                .filter(s -> {
                    String val = s.getValueAsString();
                    return val != null && val.toLowerCase().contains("memory");
                })
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Memory pressure event detected"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No memory pressure event"
            );
        }
    }

    // ========== EXCLUSION RULES ==========

    /**
     * EXCLUSION: Pod restarted due to deployment rollout.
     */
    private static class DeploymentRolloutRestartRule extends Rule.BaseRule {
        DeploymentRolloutRestartRule() {
            super(
                "oom.exclusion.deployment_rollout",
                "Pod restarted due to deployment rollout",
                RuleType.EXCLUSION,
                -10
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.KUBERNETES_EVENT)
                .filter(s -> {
                    String val = s.getValueAsString();
                    return val != null && (val.contains("rollout") || val.contains("deployment") || val.contains("update"));
                })
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Deployment rollout detected - contradicts OOMKilled"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No deployment rollout detected"
            );
        }
    }

    /**
     * EXCLUSION: Node eviction due to disk pressure.
     */
    private static class NodeEvictionDiskPressureRule extends Rule.BaseRule {
        NodeEvictionDiskPressureRule() {
            super(
                "oom.exclusion.node_disk_pressure",
                "Node eviction due to disk pressure",
                RuleType.EXCLUSION,
                -8
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.KUBERNETES_EVENT)
                .filter(s -> {
                    String val = s.getValueAsString();
                    return val != null && val.contains("evict") && val.toLowerCase().contains("disk");
                })
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Node disk pressure eviction - not OOM"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No disk pressure eviction"
            );
        }
    }

    /**
     * EXCLUSION: CrashLoopBackOff caused by configuration error.
     */
    private static class CrashLoopBackOffRule extends Rule.BaseRule {
        CrashLoopBackOffRule() {
            super(
                "oom.exclusion.crashloop_config",
                "CrashLoopBackOff caused by configuration error",
                RuleType.EXCLUSION,
                -7
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> crashLoop = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.POD_STATUS)
                .filter(s -> "CrashLoopBackOff".equals(s.getValueAsString()))
                .toList();

            if (crashLoop.isEmpty()) {
                return RuleEvaluationResult.failed(this, "No CrashLoopBackOff");
            }

            // Check for config error indicators
            List<Signal> configError = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.LOG_PATTERN)
                .filter(s -> {
                    String val = s.getValueAsString();
                    return val != null && (val.contains("config") || val.contains("Invalid"));
                })
                .toList();

            if (!configError.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    configError,
                    "CrashLoopBackOff with config error - not OOM"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "CrashLoopBackOff without config error"
            );
        }
    }

    /**
     * EXCLUSION: Application exited normally (exit code 0).
     */
    private static class ApplicationExitedNormallyRule extends Rule.BaseRule {
        ApplicationExitedNormallyRule() {
            super(
                "oom.exclusion.normal_exit",
                "Application exited normally (exit code 0)",
                RuleType.EXCLUSION,
                -10
            );
        }

        @Override
        public RuleEvaluationResult evaluate(List<Signal> signals) {
            List<Signal> matched = signals.stream()
                .filter(s -> s.getType() == Signal.SignalType.CONTAINER_STATUS)
                .filter(s -> "exitCode".equals(s.getName()))
                .filter(s -> s.getValueAsInt().orElse(-1) == 0)
                .toList();

            if (!matched.isEmpty()) {
                return RuleEvaluationResult.passed(
                    this,
                    matched,
                    "Normal exit (code 0) - contradicts OOMKilled"
                );
            }

            return RuleEvaluationResult.failed(
                this,
                "No normal exit detected"
            );
        }
    }
}
