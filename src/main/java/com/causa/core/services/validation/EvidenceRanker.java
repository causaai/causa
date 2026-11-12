package com.causa.core.services.validation;

import java.util.List;
import java.util.Map;

import com.causa.core.domain.validation.ValidationEvidence;
import com.causa.core.domain.validation.EvidenceRankingRequest.RcaSummary;
import com.causa.core.domain.validation.EvidenceRankingResponse;

/**
 * Ranks and deduplicates evidence items for diagnostic presentation.
 *
 * <p>Selects and orders existing evidence items - cannot invent new evidence.
 * Optimized for low latency and minimal cost.
 *
 * @since 0.0.1
 */
public interface EvidenceRanker {

    /**
     * Ranks and deduplicates evidence items.
     *
     * @param rca the RCA summary
     * @param evidenceList all collected evidence
     * @param validationStatus validation status (SUPPORTED, UNSUPPORTED, etc.) — drives the
     *                         refuting-priority rule
     * @param alignmentById evidence id → hypothesis alignment (SUPPORTS/REFUTES/NEUTRAL); lets the
     *                      ranker give refuting evidence higher relevance when the RCA is UNSUPPORTED
     * @return ranked evidence IDs, or all IDs if ranking fails
     */
    EvidenceRankingResponse rankAndDeduplicate(
        RcaSummary rca,
        List<ValidationEvidence> evidenceList,
        String validationStatus,
        Map<String, String> alignmentById
    );

    /**
     * Convenience overload with no alignment information (treated as all-unknown).
     */
    default EvidenceRankingResponse rankAndDeduplicate(
        RcaSummary rca,
        List<ValidationEvidence> evidenceList,
        String validationStatus
    ) {
        return rankAndDeduplicate(rca, evidenceList, validationStatus, Map.of());
    }
}
