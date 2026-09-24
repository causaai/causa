package com.causa.core.services.evidence.impl;

import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.EvidenceConstants.Ids;
import com.causa.common.constants.EvidenceConstants.Metadata;
import com.causa.common.constants.EvidenceConstants.Priority;
import com.causa.common.constants.EvidenceConstants.Snippet;
import com.causa.common.constants.EvidenceConstants.StrengthBands;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceHypothesisAlignment;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.evidence.McpSourceResolver;
import com.causa.core.services.evidence.RcaFinding;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 *       echoed back, free text like {@code "POD LOGS (recent) - sequence 124"}. It is resolved
 *       to a canonical MCP server name, with the original kept in metadata.</li>
 *   <li><strong>Narration.</strong> {@link Evidence#statement()} says what the quote shows and
 *       {@link Evidence#reasoning()} why that bears on the assertion. Both are null when the
 *       validator wrote neither, and the per-evidence reasoning falls back to the
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
            items.add(toItem(finding, result, evidence, EvidenceHypothesisAlignment.SUPPORTS, ++index));
        }
        for (Evidence evidence : result.refutingEvidence()) {
            if (carriesNoData(evidence.snippet())) {
                continue;
            }
            items.add(toItem(finding, result, evidence, EvidenceHypothesisAlignment.REFUTES, ++index));
        }

        return items;
    }

    private EvidenceItem toItem(
        RcaFinding finding,
        ValidationResult result,
        Evidence evidence,
        EvidenceHypothesisAlignment alignment,
        int index
    ) {
        EvidenceStrength strength = strengthOf(evidence.relevanceScore());

        Map<String, String> metadata = baseMetadata(finding, result);
        metadata.put(Metadata.RAW_SOURCE_LABEL, evidence.source());

        return EvidenceItem.builder()
            .id(evidenceId(result, index))
            .source(McpSourceResolver.resolve(evidence.source()))
            .type(typeOf(evidence.type()))
            .strength(strength)
            .evidenceHypothesisAlignment(alignment)
            .rawSnippet(truncate(evidence.snippet()))
            .statement(evidence.statement())
            .reasoning(reasoningOf(evidence, result))
            .confidence(evidence.relevanceScore())
            .priority(priorityOf(strength))
            .metadata(metadata)
            .build();
    }

    private Map<String, String> baseMetadata(RcaFinding finding, ValidationResult result) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(Metadata.PATH, Metadata.PATH_A);
        if (finding != null) {
            metadata.put(Metadata.FINDING_ID, finding.findingId());
            if (finding.anomalyType() != null) {
                metadata.put(Metadata.ANOMALY_TYPE, finding.anomalyType().name());
            }
        }
        metadata.put(Metadata.ASSERTION_ID, result.assertion().id());
        metadata.put(Metadata.ASSERTION_TYPE, result.assertion().type().name());
        metadata.put(Metadata.ASSERTION_STATUS, result.status().name());
        return metadata;
    }

    private String evidenceId(ValidationResult result, int index) {
        return Ids.PATH_A_PREFIX
            + result.assertion().id()
            + Ids.EVIDENCE_INFIX
            + String.format(Ids.EVIDENCE_INDEX_FORMAT, index);
    }

    /**
     * Per-evidence reasoning when the LLM wrote one, otherwise the assertion-level explanation.
     *
     * <p>The fallback is shared across every sibling item of the same assertion, so it says why
     * the assertion landed where it did rather than what this particular quote contributed.
     * Null when the validator gave neither. The assertion text is deliberately <em>not</em> a
     * fallback: it is the claim under test, and an item that restated the claim as its own
     * justification would read as though it had settled it.
     */
    private String reasoningOf(Evidence evidence, ValidationResult result) {
        if (evidence.reasoning() != null && !evidence.reasoning().isBlank()) {
            return evidence.reasoning();
        }
        return result.explanation().orElse(null);
    }

    /**
     * Detects a snippet that quotes an empty diagnostic section.
     *
     * <p>Containment rather than equality: the marker appears both bare and prefixed with the
     * section that produced it, as in {@code "GC ANALYSIS (Cryostat JFR): No Data Available"}.
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

    private int priorityOf(EvidenceStrength strength) {
        return switch (strength) {
            case DEFINITIVE -> Priority.PRIMARY;
            case STRONG -> Priority.SECONDARY;
            default -> Priority.CONTEXTUAL;
        };
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
        return snippet.substring(0, Snippet.MAX_LENGTH) + Snippet.TRUNCATION_SUFFIX;
    }
}
