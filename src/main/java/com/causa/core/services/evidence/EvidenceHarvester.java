package com.causa.core.services.evidence;

import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.ValidatedRCA;

/**
 * Evidence Harvester
 *
 * <p>Collects the evidence behind an RCA finding and maps it into the internal evidence model.
 *
 * <p>The validation pipeline already gathers everything needed: PATH A asks the LLM to find
 * evidence for each assertion, and PATH B runs deterministic rules over extracted signals.
 * A harvester reads those outputs rather than re-querying MCP servers, so collection costs
 * nothing beyond the mapping itself and cannot disagree with the verdict it explains.
 *
 * <p>Implementations are selected by anomaly type: each one knows which evidence matters for
 * the kind of finding it handles.
 *
 * @since 0.0.1
 */
public interface EvidenceHarvester {

    /**
     * Returns true when this harvester handles findings of the given anomaly type.
     *
     * @param anomalyType the classified anomaly; may be null
     * @return true if {@link #harvest} should be called for this finding
     */
    boolean supports(AnomalyType anomalyType);

    /**
     * Collects all evidence for a finding.
     *
     * <p>Never throws on partial data: a missing validation path, an empty diagnostic section,
     * or an unrecognised source yields a recorded gap rather than a failure. Evidence
     * collection is additive to a diagnostic and must not be able to fail one.
     *
     * @param finding           the RCA finding evidence is collected for
     * @param validatedRca      the completed validation, carrying both paths; may be null
     * @param diagnosticContext the rendered MCP context both paths were run against, used to
     *                          recover the source text behind each piece of evidence; may be null
     * @return the collected evidence, empty when there was nothing to harvest
     */
    EvidenceCollectionResult harvest(RcaFinding finding, ValidatedRCA validatedRca,
                                     String diagnosticContext);
}
