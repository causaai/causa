package com.causa.core.services.evidence;

import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;

/**
 * RCA Finding
 *
 * <p>The subject of an evidence collection run: the specific root cause claim that collected
 * evidence is gathered for and attributed to.
 *
 * <p>Carrying the finding as its own value — rather than passing the whole
 * {@link RootCauseAnalysis} around — is what lets every emitted
 * {@link com.causa.core.domain.validation.EvidenceItem} be traced back to the diagnosis it
 * belongs to, and lets a collector decide whether it handles this kind of finding at all.
 *
 * @param findingId   the diagnostic ID this finding belongs to
 * @param anomalyType the classified anomaly, which selects the applicable collector
 * @param issueTitle  human-readable title, retained for logging and evidence reasoning text
 *
 * @since 0.0.1
 */
public record RcaFinding(
    String findingId,
    AnomalyType anomalyType,
    String issueTitle
) {

    /**
     * Creates a finding from a diagnostic ID and its root cause analysis.
     *
     * @param diagnosticId the diagnostic this RCA belongs to
     * @param rca          the root cause analysis; may be null
     * @return the finding, or null when there is no RCA to attribute evidence to
     */
    public static RcaFinding from(String diagnosticId, RootCauseAnalysis rca) {
        if (rca == null) {
            return null;
        }
        return new RcaFinding(diagnosticId, rca.anomalyType(), rca.issueTitle());
    }
}
