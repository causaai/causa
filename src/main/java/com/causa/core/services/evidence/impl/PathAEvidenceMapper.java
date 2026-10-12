package com.causa.core.services.evidence.impl;

import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.EvidenceConstants.Ids;
import com.causa.common.constants.EvidenceConstants.Snippet;
import com.causa.common.constants.EvidenceConstants.StrengthBands;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.evidence.McpSourceResolver;
import com.causa.core.services.evidence.RcaFinding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * PATH A Evidence Mapper
 *
 * <p>Maps assertion-based validation output — the evidence the LLM found while checking each
 * claim the RCA made — into {@link EvidenceItem}.
 *
 * <p>Two things get corrected on the way through:
 *
 * <ul>
 *   <li><strong>Source.</strong> {@link Evidence#source()} is whatever section label the LLM
 *       echoed back, free text like {@code "POD_LOGS - sequence 124"}. It is resolved
 *       to the MCP server that declared that section in {@code mcp.json}.</li>
 *   <li><strong>Narration.</strong> {@link Evidence#statement()} says what the quote shows and
 *       {@link Evidence#explanation()} why that bears on the assertion. Both are null when the
 *       validator wrote neither, and the per-evidence explanation falls back to the
 *       assertion-level explanation.</li>
 *   <li><strong>Empty sections cited as proof.</strong> The LLM will happily quote
 *       {@code "No Data Available"} as supporting evidence at a high relevance score. Such an
 *       item is dropped rather than mapped: a diagnosis does not report its blind spots, so
 *       carrying the item only to hide it everywhere downstream buys nothing.</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class PathAEvidenceMapper {

    private final McpSourceResolver sourceResolver;

    @Inject
    public PathAEvidenceMapper(McpSourceResolver sourceResolver) {
        this.sourceResolver = sourceResolver;
    }

    /**
     * Maps a single assertion's validation result into evidence items.
     *
     * @param finding the RCA finding this evidence is attributed to
     * @param result  the validation result for one assertion
     * @return one item per quoted evidence piece; empty when the assertion produced none that
     *         rest on real source output
     */
    public List<EvidenceItem> map(RcaFinding finding, ValidationResult result) {
        if (result == null) {
            return List.of();
        }

        List<EvidenceItem> items = new ArrayList<>();
        int index = 0;

        for (Evidence evidence : result.supportingEvidence()) {
            if (carriesNoData(evidence.snippet())) {
                continue;
            }
            items.add(toItem(result, evidence, ++index));
        }
        for (Evidence evidence : result.refutingEvidence()) {
            if (carriesNoData(evidence.snippet())) {
                continue;
            }
            items.add(toItem(result, evidence, ++index));
        }

        return items;
    }

    private EvidenceItem toItem(ValidationResult result, Evidence evidence, int index) {
        return EvidenceItem.builder()
            .id(evidenceId(result, index))
            .source(sourceResolver.resolve(evidence.source()))
            .type(typeOf(evidence.type()))
            .strength(strengthOf(evidence.relevanceScore()))
            .rawSnippet(truncate(evidence.snippet()))
            .statement(evidence.statement())
            .explanation(explanationOf(evidence, result))
            .confidence(evidence.relevanceScore())
            .build();
    }

    private String evidenceId(ValidationResult result, int index) {
        return Ids.PATH_A_PREFIX
            + result.assertion().id()
            + Ids.EVIDENCE_INFIX
            + String.format(Ids.EVIDENCE_INDEX_FORMAT, index);
    }

    /**
     * Per-evidence explanation when the LLM wrote one, otherwise the assertion-level explanation.
     *
     * <p>The fallback is shared across every sibling item of the same assertion, so it says why
     * the assertion landed where it did rather than what this particular quote contributed.
     * Null when the validator gave neither. The assertion text is deliberately <em>not</em> a
     * fallback: it is the claim under test, and an item that restated the claim as its own
     * justification would read as though it had settled it.
     */
    private String explanationOf(Evidence evidence, ValidationResult result) {
        if (evidence.explanation() != null && !evidence.explanation().isBlank()) {
            return evidence.explanation();
        }
        return result.explanation().orElse(null);
    }

    /**
     * Detects a snippet that quotes an empty diagnostic section.
     *
     * <p>Containment rather than equality: the marker appears both bare and prefixed with the
     * section that produced it, as in {@code "GC_ANALYSIS: No Data Available"}.
     */
    private boolean carriesNoData(String snippet) {
        return snippet != null && snippet.contains(ContextConstants.NOT_AVAILABLE);
    }

    private EvidenceStrength strengthOf(double relevanceScore) {
        if (relevanceScore >= StrengthBands.DEFINITIVE) {
            return EvidenceStrength.DEFINITIVE;
        }
        if (relevanceScore >= StrengthBands.STRONG) {
            return EvidenceStrength.STRONG;
        }
        if (relevanceScore >= StrengthBands.MODERATE) {
            return EvidenceStrength.MODERATE;
        }
        if (relevanceScore >= StrengthBands.WEAK) {
            return EvidenceStrength.WEAK;
        }
        return EvidenceStrength.CIRCUMSTANTIAL;
    }

    private EvidenceItem.EvidenceType typeOf(Evidence.EvidenceType type) {
        if (type == null) {
            return EvidenceItem.EvidenceType.OTHER;
        }
        return switch (type) {
            case KUBERNETES_EVENT -> EvidenceItem.EvidenceType.KUBERNETES_EVENT;
            case POD_LOG -> EvidenceItem.EvidenceType.LOG_PATTERN;
            case METRIC -> EvidenceItem.EvidenceType.METRIC;
            case KRUIZE_RECOMMENDATION -> EvidenceItem.EvidenceType.RECOMMENDATION;
            case GC_ANALYSIS -> EvidenceItem.EvidenceType.GC_ANALYSIS;
            case MEMORY_ANALYSIS -> EvidenceItem.EvidenceType.MEMORY_ANALYSIS;
            case THREAD_ANALYSIS -> EvidenceItem.EvidenceType.THREAD_ANALYSIS;
            case CRYOSTAT_ANALYSIS, EXCEPTION_ANALYSIS -> EvidenceItem.EvidenceType.JVM_ANALYSIS;
            case OTHER -> EvidenceItem.EvidenceType.OTHER;
        };
    }

    private String truncate(String snippet) {
        if (snippet == null || snippet.length() <= Snippet.MAX_LENGTH) {
            return snippet;
        }
        return snippet.substring(0, Snippet.MAX_LENGTH - Snippet.TRUNCATION_SUFFIX.length())
            + Snippet.TRUNCATION_SUFFIX;
    }
}
