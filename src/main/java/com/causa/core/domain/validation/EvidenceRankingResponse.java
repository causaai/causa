package com.causa.core.domain.validation;

import java.util.List;

/**
 * Response from evidence ranking and deduplication agent.
 *
 * <p>Contains the ranked and deduplicated evidence IDs in priority order.
 *
 * @since 0.0.1
 */
public record EvidenceRankingResponse(
    List<String> rankedEvidenceIds,
    String reasoning
) {

    /**
     * Validates the response.
     */
    public EvidenceRankingResponse {
        if (rankedEvidenceIds == null) {
            rankedEvidenceIds = List.of();
        }
        // Make list immutable
        rankedEvidenceIds = List.copyOf(rankedEvidenceIds);
    }

    /**
     * Checks if the response is empty.
     */
    public boolean isEmpty() {
        return rankedEvidenceIds.isEmpty();
    }

    /**
     * Gets the number of ranked evidence items.
     */
    public int size() {
        return rankedEvidenceIds.size();
    }

    /**
     * Checks if a specific evidence ID is in the ranked list.
     */
    public boolean contains(String evidenceId) {
        return rankedEvidenceIds.contains(evidenceId);
    }
}
