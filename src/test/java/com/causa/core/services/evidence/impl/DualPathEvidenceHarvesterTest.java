package com.causa.core.services.evidence.impl;

import com.causa.core.domain.RootCauseAnalysis.AnomalyType;
import com.causa.core.services.evidence.EvidenceHarvester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The harvester is anomaly-agnostic — both mappers read validation output, never the
 * hypothesis — so the gate is the only place a kind of finding can be turned away.
 */
class DualPathEvidenceHarvesterTest {

    private final EvidenceHarvester harvester = new DualPathEvidenceHarvester();

    @Test
    void harvestsEveryDiagnosedAnomaly() {
        assertThat(harvester.supports(AnomalyType.OOM_KILLED)).isTrue();
        assertThat(harvester.supports(AnomalyType.POSSIBLE_OOM_KILLED)).isTrue();
        assertThat(harvester.supports(AnomalyType.POSSIBLE_GC_PAUSE)).isTrue();
    }

    /**
     * A finding that claims nothing went wrong has nothing to evidence, and an unclassified one
     * has no ruleset behind it either. Both yield a skip rather than an empty evidence panel.
     */
    @Test
    void harvestsNothingWhenThereIsNoAnomalyToEvidence() {
        assertThat(harvester.supports(AnomalyType.HEALTHY)).isFalse();
        assertThat(harvester.supports(null)).isFalse();
    }
}
