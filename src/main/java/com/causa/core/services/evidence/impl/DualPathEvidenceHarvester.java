package com.causa.core.services.evidence.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.DiagnosticContextIndex;
import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.ValidatedRCA;
import com.causa.core.domain.validation.ValidationResult;
import com.causa.core.services.evidence.EvidenceCollectionResult;
import com.causa.core.services.evidence.EvidenceHarvester;
import com.causa.core.services.evidence.RcaFinding;
import com.causa.core.services.rules.HypothesisValidationResult;
import com.causa.core.services.rules.RuleEvaluationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Dual-Path Evidence Harvester
 *
 * <p>Collects evidence for any diagnosed finding from both validation paths.
 *
 * <p>Nothing here is specific to a kind of anomaly. PATH A reads whatever assertions the RCA
 * made, PATH B reads whatever ruleset {@code RuleEngine} selected for the anomaly type, and
 * the mapping in between is driven by the validation output rather than by the hypothesis.
 * A per-anomaly harvester would therefore be this class with a different {@link #supports}.
 *
 * <p>Only {@code HEALTHY} is excluded: a finding that claims nothing went wrong has no
 * evidence to gather. Suspected variants are handled the same as confirmed ones — the point
 * of collecting evidence is to establish which the data actually supports, so gating on the
 * confident variant alone would skip exactly the findings that most need checking.
 *
 * <p>Both paths are harvested independently and neither is required: PATH A alone still
 * produces evidence when the rule engine had no ruleset to run, and a harvest over an empty
 * validation yields an empty result rather than an error.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DualPathEvidenceHarvester implements EvidenceHarvester {

    private static final CausaLogger log = CausaLogger.getLogger(DualPathEvidenceHarvester.class);

    @Inject
    PathAEvidenceMapper pathAMapper;

    @Inject
    PathBEvidenceMapper pathBMapper;

    @Override
    public boolean supports(AnomalyType anomalyType) {
        return anomalyType != null && anomalyType != AnomalyType.HEALTHY;
    }

    @Override
    public EvidenceCollectionResult harvest(RcaFinding finding, ValidatedRCA validatedRca,
                                            String diagnosticContext) {
        String findingId = finding != null ? finding.findingId() : null;

        if (validatedRca == null) {
            log.info(LogMessages.Evidence.NO_VALIDATION_RESULT)
                .field("findingId", findingId)
                .log();
            return EvidenceCollectionResult.empty(findingId);
        }

        log.info(LogMessages.Evidence.COLLECTION_STARTED)
            .field("findingId", findingId)
            .field("anomalyType", finding != null ? finding.anomalyType() : null)
            .log();

        List<EvidenceItem> items = new ArrayList<>();
        items.addAll(harvestPathA(finding, validatedRca));
        items.addAll(harvestPathB(finding, validatedRca));

        EvidenceCollectionResult result = new EvidenceCollectionResult(findingId, items);

        log.info(LogMessages.Evidence.COLLECTION_COMPLETED)
            .field("findingId", findingId)
            .field("total", result.totalCount())
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

    private List<EvidenceItem> harvestPathB(RcaFinding finding, ValidatedRCA validatedRca) {
        if (validatedRca.dualValidation() == null
            || validatedRca.dualValidation().ruleBasedVerdict() == null) {
            log.info(LogMessages.Evidence.PATH_B_UNAVAILABLE)
                .field("findingId", finding != null ? finding.findingId() : null)
                .log();
            return List.of();
        }

        HypothesisValidationResult ruleVerdict = validatedRca.dualValidation().ruleBasedVerdict();

        List<EvidenceItem> items = new ArrayList<>();
        for (RuleEvaluationResult result : ruleVerdict.getAllResults()) {
            EvidenceItem item = pathBMapper.map(finding, result);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }
}
