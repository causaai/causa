package com.causa.core.services.validation;

import java.util.List;

import com.causa.core.domain.validation.Evidence;
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
     * @param validationScore final validation score (0.0 - 1.0)
     * @param validationStatus validation status (SUPPORTED, UNSUPPORTED, etc.)
     * @return ranked evidence IDs, or all IDs if ranking fails
     */
    EvidenceRankingResponse rankAndDeduplicate(
        RcaSummary rca,
        List<Evidence> evidenceList,
        double validationScore,
        String validationStatus
    );
}
