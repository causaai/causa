package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants.Ids;
import com.causa.common.constants.EvidenceConstants.Messages;
import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.common.constants.EvidenceConstants.Priority;
import com.causa.common.constants.EvidenceConstants.RuleConfidence;
import com.causa.common.constants.EvidenceConstants.RuleWeightBands;
import com.causa.common.constants.EvidenceConstants.Snippet;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceHypothesisAlignment;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.services.evidence.McpSourceResolver;
import com.causa.core.services.evidence.RcaFinding;
import com.causa.core.services.rules.Rule;
import com.causa.core.services.rules.RuleEvaluationResult;
import com.causa.core.services.rules.RuleType;
import com.causa.core.services.rules.Signal;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * PATH B Evidence Mapper
 *
 * <p>Maps deterministic rule evaluation into {@link EvidenceItem}.
 *
 * <p>Rules that <em>failed</em> are mapped as deliberately as rules that passed. A failed
 * REQUIRED rule is frequently the sharpest fact in a diagnosis — "exit code was 143, not 137"
 * is what distinguishes a JVM heap exhaustion from a kernel OOM kill — and discarding it
 * leaves the verdict unexplained.
 *
 * <p>Whether a failure is a finding or a gap depends on
 * {@link RuleEvaluationResult#getInspectedSignals()}: a signal that was examined and held the
 * wrong value {@link EvidenceHypothesisAlignment#REFUTES refutes} the hypothesis, while a rule
 * that inspected nothing produces no evidence at all — it read no source output, so it has
 * none to show.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class PathBEvidenceMapper {

    /**
     * Maps one rule evaluation into an evidence item.
     *
     * @param finding the RCA finding this evidence is attributed to
     * @param result  the outcome of evaluating a single rule
     * @return the evidence item, or null when there is no rule to describe or the rule
     *         inspected no signal and so has no source output to offer
     */
    public EvidenceItem map(RcaFinding finding, RuleEvaluationResult result) {
        if (result == null || result.getRule() == null) {
            return null;
        }

        Rule rule = result.getRule();
        RuleType ruleType = rule.getType();
        boolean passed = result.isPassed();

        List<Signal> inspected = result.getInspectedSignals();
        Signal signal = passed
            ? first(result.getMatchedSignals()).orElseGet(() -> first(inspected).orElse(null))
            : first(inspected).orElse(null);

        if (signal == null) {
            return null;
        }

        EvidenceStrength strength = strengthOf(rule.getWeight());
        EvidenceHypothesisAlignment alignment;
        double confidence;
        int priority;

        if (ruleType == RuleType.EXCLUSION) {
            if (passed) {
                // A matched exclusion is contradictory evidence: something other than the
                // hypothesis explains the termination.
                alignment = EvidenceHypothesisAlignment.REFUTES;
                confidence = RuleConfidence.REFUTED;
                priority = priorityOf(strength);
            } else {
                // A ruled-out alternative. Worth recording, never worth showing.
                alignment = EvidenceHypothesisAlignment.NEUTRAL;
                confidence = RuleConfidence.RULED_OUT;
                strength = EvidenceStrength.CIRCUMSTANTIAL;
                priority = Priority.BACKGROUND;
            }
        } else if (passed) {
            alignment = EvidenceHypothesisAlignment.SUPPORTS;
            confidence = ruleType == RuleType.REQUIRED
                ? RuleConfidence.PASSED_REQUIRED
                : RuleConfidence.PASSED_SUPPORTING;
            priority = priorityOf(strength);
        } else {
            alignment = EvidenceHypothesisAlignment.REFUTES;
            confidence = RuleConfidence.REFUTED;
            priority = priorityOf(strength);
        }

        return EvidenceItem.builder()
            .id(Ids.PATH_B_PREFIX + rule.getId())
            .source(resolveSource(signal))
            .type(typeOf(signal))
            .strength(strength)
            .evidenceHypothesisAlignment(alignment)
            .rawSnippet(snippetOf(signal))
            .statement(statementOf(result))
            .reasoning(reasoningOf(rule, result))
            .confidence(confidence)
            .priority(priority)
            .metadata(metadataOf(finding, rule, result, signal))
            .build();
    }

    /**
     * Resolves the MCP server behind a rule outcome from the signal the rule read.
     *
     * <p>A signal carrying no section label resolves to {@link McpSourceResolver#resolve(String)
     * unknown}, and deliberately so. Rules match on signal type and name, never on server: the
     * same signal can arrive from several MCP servers — a heap trend from Cryostat, Quarkus or
     * JMX — and servers are plug-and-play, so naming a source the rule never named would
     * attribute the fact to a server that may not even be in the deployment.
     */
    private String resolveSource(Signal signal) {
        return McpSourceResolver.resolve(
            signal.getMetadata(Metadata.SIGNAL_SECTION).map(Object::toString).orElse(null)
        );
    }

    private Map<String, String> metadataOf(
        RcaFinding finding,
        Rule rule,
        RuleEvaluationResult result,
        Signal signal
    ) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(Metadata.PATH, Metadata.PATH_B);
        if (finding != null) {
            metadata.put(Metadata.FINDING_ID, finding.findingId());
            if (finding.anomalyType() != null) {
                metadata.put(Metadata.ANOMALY_TYPE, finding.anomalyType().name());
            }
        }
        metadata.put(Metadata.RULE_ID, rule.getId());
        metadata.put(Metadata.RULE_TYPE, rule.getType().name());
        metadata.put(Metadata.RULE_WEIGHT, String.valueOf(rule.getWeight()));
        metadata.put(Metadata.RULE_PASSED, String.valueOf(result.isPassed()));
        metadata.put(Metadata.INSPECTED_SIGNAL, signal.getName());
        signal.getMetadata(Metadata.SIGNAL_SECTION)
            .ifPresent(section -> metadata.put(Metadata.RAW_SOURCE_LABEL, section.toString()));
        return metadata;
    }

    /**
     * The context line the signal was read from, verbatim.
     *
     * <p>Falls back to {@code name: value} for a signal carrying no line — the normalised pair
     * is not source output, but it is what the rule saw.
     */
    private String snippetOf(Signal signal) {
        return truncate(signal.getMetadata(Metadata.SIGNAL_SNIPPET)
            .map(Object::toString)
            .orElseGet(() -> String.format(
                Messages.SIGNAL_SNIPPET_FORMAT,
                signal.getName(),
                signal.getValueAsString()
            )));
    }

    /**
     * The rule's outcome message stands in for a statement of what was observed.
     *
     * <p>It is the ruleset author's own words — {@code messages.success} or
     * {@code messages.failure} — so it reads as prose rather than as a log line, which is the
     * point: {@code rawSnippet} is unreadable at the altitude a statement is scanned at. It is
     * also generic, naming no observed value, and {@link #reasoningOf} wraps the same sentence,
     * so the item says it twice. Accepted for now; see
     * {@code docs/path-b-evidence-statement-design.md} option C for the per-rule statement
     * templates that replace this.
     */
    private String statementOf(RuleEvaluationResult result) {
        return result.getReasoning();
    }

    private String reasoningOf(Rule rule, RuleEvaluationResult result) {
        String format = result.isPassed() ? Messages.RULE_PASSED_FORMAT : Messages.RULE_FAILED_FORMAT;
        return String.format(format, titleCase(rule.getType().name()), result.getReasoning());
    }

    /**
     * Rule weight stands in for relevance: the ruleset author already expressed how decisive
     * each condition is, and exclusion weights are negative, so magnitude is what matters.
     */
    private EvidenceStrength strengthOf(int weight) {
        int magnitude = Math.abs(weight);
        if (magnitude >= RuleWeightBands.DEFINITIVE) {
            return EvidenceStrength.DEFINITIVE;
        }
        if (magnitude >= RuleWeightBands.STRONG) {
            return EvidenceStrength.STRONG;
        }
        if (magnitude >= RuleWeightBands.MODERATE) {
            return EvidenceStrength.MODERATE;
        }
        if (magnitude > 0) {
            return EvidenceStrength.WEAK;
        }
        return EvidenceStrength.CIRCUMSTANTIAL;
    }

    private int priorityOf(EvidenceStrength strength) {
        return switch (strength) {
            case DEFINITIVE -> Priority.PRIMARY;
            case STRONG -> Priority.SECONDARY;
            default -> Priority.CONTEXTUAL;
        };
    }

    private EvidenceItem.EvidenceType typeOf(Signal signal) {
        if (signal.getType() == null) {
            return EvidenceItem.EvidenceType.OTHER;
        }
        return switch (signal.getType()) {
            case KUBERNETES_EVENT -> EvidenceItem.EvidenceType.KUBERNETES_EVENT;
            case POD_STATUS -> EvidenceItem.EvidenceType.POD_STATUS;
            case CONTAINER_STATUS -> EvidenceItem.EvidenceType.CONTAINER_STATUS;
            case METRIC -> EvidenceItem.EvidenceType.METRIC;
            case LOG_PATTERN -> EvidenceItem.EvidenceType.LOG_PATTERN;
            case KRUIZE_RECOMMENDATION -> EvidenceItem.EvidenceType.RECOMMENDATION;
            case JVM_ANALYSIS, CRYOSTAT_ANALYSIS -> EvidenceItem.EvidenceType.JVM_ANALYSIS;
            case TRACE -> EvidenceItem.EvidenceType.OTHER;
        };
    }

    private String titleCase(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String truncate(String snippet) {
        if (snippet == null || snippet.length() <= Snippet.MAX_LENGTH) {
            return snippet;
        }
        return snippet.substring(0, Snippet.MAX_LENGTH) + Snippet.TRUNCATION_SUFFIX;
    }

    private Optional<Signal> first(List<Signal> signals) {
        return signals == null || signals.isEmpty() ? Optional.empty() : Optional.of(signals.get(0));
    }
}
