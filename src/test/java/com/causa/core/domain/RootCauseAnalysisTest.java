package com.causa.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RootCauseAnalysis Tests")
class RootCauseAnalysisTest {

    @Test void confidenceSummary_record() {
        RootCauseAnalysis.ConfidenceSummary cs = new RootCauseAnalysis.ConfidenceSummary(0.95, "High confidence");
        assertThat(cs.rcaConfidenceScore()).isEqualTo(0.95);
        assertThat(cs.summaryText()).isEqualTo("High confidence");
    }

    @Test void recommendation_record() {
        RootCauseAnalysis.Recommendation rec = new RootCauseAnalysis.Recommendation(
            "Immediate Mitigation", "Restart pod", "Restart the failing pod",
            "kubectl rollout restart", 0.9, List.of("check CPU"));
        assertThat(rec.solutionType()).isEqualTo("Immediate Mitigation");
        assertThat(rec.solutionTitle()).isEqualTo("Restart pod");
        assertThat(rec.solutionConfidenceScore()).isEqualTo(0.9);
    }

    @Test void anomalyType_values() {
        assertThat(RootCauseAnalysis.AnomalyType.values())
            .containsExactlyInAnyOrder(
                RootCauseAnalysis.AnomalyType.OOM_KILLED,
                RootCauseAnalysis.AnomalyType.POSSIBLE_OOM_KILLED,
                RootCauseAnalysis.AnomalyType.POSSIBLE_GC_PAUSE,
                RootCauseAnalysis.AnomalyType.HEALTHY
            );
    }

    @Test void fullRecord() {
        RootCauseAnalysis.ConfidenceSummary cs = new RootCauseAnalysis.ConfidenceSummary(0.8, "ok");
        RootCauseAnalysis rca = new RootCauseAnalysis(
            "title", "summary", "desc", "tech desc",
            RootCauseAnalysis.AnomalyType.OOM_KILLED, "root cause",
            List.of("log1"), List.of("evidence1"),
            List.of(new RootCauseAnalysis.Recommendation("Immediate Mitigation","t","d","n",0.7,List.of())),
            cs, "notes"
        );
        assertThat(rca.issueTitle()).isEqualTo("title");
        assertThat(rca.anomalyType()).isEqualTo(RootCauseAnalysis.AnomalyType.OOM_KILLED);
        assertThat(rca.confidenceSummary().rcaConfidenceScore()).isEqualTo(0.8);
        assertThat(rca.llmNotes()).isEqualTo("notes");
    }
}
