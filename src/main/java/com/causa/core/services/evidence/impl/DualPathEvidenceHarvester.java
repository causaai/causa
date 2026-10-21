package com.causa.core.services.evidence.impl;

import com.causa.common.constants.DiagnosticConstants.LogFields;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.ValidatedRCA;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.evidence.EvidenceCollectionResult;
import com.causa.core.services.evidence.EvidenceHarvester;
import com.causa.core.services.evidence.RcaFinding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Dual-Path ValidationEvidence Harvester
 *
 * <p>Collects evidence for any diagnosed finding from the LLM assertion path (PATH A). The
 * rule-derived path (PATH B) lands in a later change; rule signals do not yet carry the
 * source line they were read from, so evidence mapped from them could not be attributed.
 *
 * <p>Nothing here is specific to a kind of anomaly. PATH A reads whatever assertions the RCA
 * made, and the mapping is driven by the validation output rather than by the hypothesis.
 * A per-anomaly harvester would therefore be this class with a different {@link #supports}.
 *
 * <p>Only {@code HEALTHY} is excluded: a finding that claims nothing went wrong has no
 * evidence to gather. Suspected variants are handled the same as confirmed ones — the point
 * of collecting evidence is to establish which the data actually supports, so gating on the
 * confident variant alone would skip exactly the findings that most need checking.
 *
 * <p>A harvest over an empty validation yields an empty result rather than an error.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DualPathEvidenceHarvester implements EvidenceHarvester {

    private static final CausaLogger log = CausaLogger.getLogger(DualPathEvidenceHarvester.class);

    @Inject
    PathAEvidenceMapper pathAMapper;

    @Override
    public boolean supports(AnomalyType anomalyType) {
        return anomalyType != null && anomalyType != AnomalyType.HEALTHY;
    }

    @Override
    public EvidenceCollectionResult harvest(RcaFinding finding, ValidatedRCA validatedRca,
                                            String diagnosticContext) {
        String diagnosticId = finding != null ? finding.diagnosticId() : null;

        if (validatedRca == null) {
            log.info(LogMessages.ValidationEvidence.NO_VALIDATION_RESULT)
                .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
                .log();
            return EvidenceCollectionResult.empty(diagnosticId);
        }

        log.info(LogMessages.ValidationEvidence.COLLECTION_STARTED)
            .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
            .field("anomalyType", finding != null ? finding.anomalyType() : null)
            .log();

        EvidenceCollectionResult result = new EvidenceCollectionResult(
            diagnosticId, harvestPathA(finding, validatedRca));

        log.info(LogMessages.ValidationEvidence.COLLECTION_COMPLETED)
            .field(LogFields.DIAGNOSTIC_ID, diagnosticId)
            .field("total", result.totalCount())
            .field("refuting", result.refutingCount())
            .field("sources", String.join(",", result.contributingSources()))
            .log();

        return result;
    }

    private List<EvidenceItem> harvestPathA(RcaFinding finding, ValidatedRCA validatedRca) {
        List<ValidationResult> results = validatedRca.validationResults();
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<EvidenceItem> items = new ArrayList<>();
        for (ValidationResult result : results) {
            items.addAll(pathAMapper.map(finding, result));
        }
        return items;
    }
}
