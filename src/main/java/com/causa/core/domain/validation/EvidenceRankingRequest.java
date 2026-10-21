package com.causa.core.domain.validation;

import java.util.List;

/**
 * Request for evidence ranking and deduplication agent.
 *
 * <p>Contains the RCA analysis, all collected evidence, and validation score
 * for the agent to rank and deduplicate evidence items.
 *
 * @since 0.0.1
 */
public record EvidenceRankingRequest(
    RcaSummary rca,
    List<Evidence> evidenceList,
    double validationScore,
    String validationStatus
) {

    /**
     * Simplified RCA summary for the agent — only the issue summary and root cause.
     */
    public record RcaSummary(
        String issueSummary,
        String rootCause
    ) {}

    /**
     * Validates the request.
     */
    public EvidenceRankingRequest {
        if (rca == null) {
            throw new IllegalArgumentException("RCA summary cannot be null");
        }
        if (evidenceList == null) {
            evidenceList = List.of();
        }
        if (validationScore < 0.0 || validationScore > 1.0) {
            throw new IllegalArgumentException("Validation score must be between 0.0 and 1.0");
        }
        if (validationStatus == null || validationStatus.isBlank()) {
            throw new IllegalArgumentException("Validation status cannot be null or blank");
        }
    }
}
