package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants.Ids;
import com.causa.common.constants.EvidenceConstants.RuleEvidenceMessages;
import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.common.constants.EvidenceConstants.RuleConfidence;
import com.causa.common.constants.EvidenceConstants.RuleWeightBands;
import com.causa.common.constants.EvidenceConstants.Snippet;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.services.evidence.McpSourceResolver;
import com.causa.core.services.evidence.RcaFinding;
import com.causa.core.services.rules.Rule;
import com.causa.core.services.rules.RuleEvaluationResult;
import com.causa.core.services.rules.RuleType;
import com.causa.core.services.rules.Signal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * PATH B Evidence Mapper
 *
 * <p>Maps deterministic rule evaluation into {@link EvidenceItem}.
 *
 * <p>Only rules that matched a signal produce evidence. A rule that matched nothing has no
 * source output to show, so it is skipped — the rule score already records that it did not
 * hold.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class PathBEvidenceMapper {

    private final McpSourceResolver sourceResolver;

    @Inject
    public PathBEvidenceMapper(McpSourceResolver sourceResolver) {
        this.sourceResolver = sourceResolver;
    }

    /**
     * Maps one rule evaluation into an evidence item.
     *
     * @param finding the RCA finding this evidence is attributed to
     * @param result  the outcome of evaluating a single rule
     * @return the evidence item, or null when there is no rule to describe or the rule
     *         matched no signal and so has no source output to offer
     */
    public EvidenceItem map(RcaFinding finding, RuleEvaluationResult result) {
        if (result == null || result.getRule() == null) {
            return null;
        }

        Rule rule = result.getRule();
        RuleType ruleType = rule.getType();

        Signal signal = first(result.getMatchedSignals()).orElse(null);

        if (signal == null) {
            return null;
        }

        // Only matched signals reach here, so the rule passed. A matched EXCLUSION contradicts
        // the hypothesis: something other than it explains the termination.
        double confidence = switch (ruleType) {
            case EXCLUSION -> RuleConfidence.REFUTED;
            case REQUIRED -> RuleConfidence.PASSED_REQUIRED;
            default -> RuleConfidence.PASSED_SUPPORTING;
        };

        return EvidenceItem.builder()
            .id(Ids.PATH_B_PREFIX + rule.getId())
            .source(resolveSource(signal))
            .type(typeOf(signal))
            .strength(strengthOf(rule.getWeight()))
            .evidenceHypothesisAlignment(alignmentOf(ruleType))
            .rawSnippet(snippetOf(signal))
            .statement(statementOf(result))
            .explanation(reasoningOf(rule, result))
            .confidence(confidence)
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
        return sourceResolver.resolve(
            signal.getMetadata(Metadata.SIGNAL_SECTION).map(Object::toString).orElse(null)
        );
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
                RuleEvidenceMessages.SIGNAL_SNIPPET_FORMAT,
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
        String format = result.isPassed()
            ? RuleEvidenceMessages.RULE_PASSED_FORMAT
            : RuleEvidenceMessages.RULE_FAILED_FORMAT;
        return String.format(format, titleCase(rule.getType().name()), result.getReasoning());
    }

    /**
     * Which way a matched rule points.
     *
     * <p>This is the axis {@link #strengthOf} deliberately drops: a matched exclusion at
     * weight -10 is every bit as decisive as a required rule at +10, it just decides the
     * other way. Keeping direction here is what lets strength stay a single comparable
     * scale — {@code DEFINITIVE / REFUTES} rather than a strength value that doubles as a
     * verdict and has to be special-cased by everything that ranks evidence.
     */
    private EvidenceItem.EvidenceHypothesisAlignment alignmentOf(RuleType ruleType) {
        return ruleType == RuleType.EXCLUSION
            ? EvidenceItem.EvidenceHypothesisAlignment.REFUTES
            : EvidenceItem.EvidenceHypothesisAlignment.SUPPORTS;
    }

    /**
     * Rule weight stands in for relevance: the ruleset author already expressed how decisive
     * each condition is, and exclusion weights are negative, so magnitude is what matters.
     * Which way that weight points is {@link #alignmentOf}, not a strength band of its own.
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
        if (magnitude >= RuleWeightBands.WEAK) {
            return EvidenceStrength.WEAK;
        }
        return EvidenceStrength.CIRCUMSTANTIAL;
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
