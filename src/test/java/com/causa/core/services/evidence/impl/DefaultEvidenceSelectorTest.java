package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants.Priority;
import com.causa.common.constants.EvidenceConstants.Selection;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceHypothesisAlignment;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.domain.validation.EvidenceItem.EvidenceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The selected panel is what the API renders and is narrowed once, at diagnostic completion.
 * These cover the budget arithmetic in {@link DefaultEvidenceSelector#select(List)}: the panel
 * never exceeds {@link Selection#MAX_UI_EVIDENCE}, and contradictions get their reserved slots
 * before supporting evidence claims the rest.
 */
class DefaultEvidenceSelectorTest {

    private final DefaultEvidenceSelector selector = new DefaultEvidenceSelector();

    @Test
    void neverExceedsThePanelBudget() {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < Selection.MAX_UI_EVIDENCE * 3; i++) {
            items.add(supporting("s" + i, EvidenceStrength.DEFINITIVE, 0.99));
        }

        assertThat(selector.select(items)).hasSize(Selection.MAX_UI_EVIDENCE);
    }

    @Test
    void reservesSlotsForContradictionsBeforeSupportingEvidence() {
        List<EvidenceItem> items = new ArrayList<>();
        // Plenty of supporting evidence, all at the top confidence a supporting item can reach.
        for (int i = 0; i < Selection.MAX_UI_EVIDENCE * 2; i++) {
            items.add(supporting("s" + i, EvidenceStrength.DEFINITIVE, 1.0));
        }
        items.add(refuting("r1"));

        List<EvidenceItem> selected = selector.select(items);

        assertThat(selected).hasSize(Selection.MAX_UI_EVIDENCE);
        assertThat(selected).extracting(EvidenceItem::id).contains("r1");
    }

    @Test
    void stopsShortOfTheBudgetRatherThanPaddingWithWeakerEvidence() {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(supporting("strong" + i, EvidenceStrength.STRONG, 0.9));
        }
        for (int i = 0; i < 10; i++) {
            items.add(supporting("moderate" + i, EvidenceStrength.MODERATE, 0.7));
        }

        List<EvidenceItem> selected = selector.select(items);

        assertThat(selected).hasSize(5);
        assertThat(selected).extracting(EvidenceItem::id).noneMatch(id -> id.startsWith("moderate"));
    }

    @Test
    void admitsWeakerEvidenceWhenTheFindingHasTooLittleSolidBacking() {
        List<EvidenceItem> items = new ArrayList<>();
        items.add(supporting("strong0", EvidenceStrength.STRONG, 0.9));
        for (int i = 0; i < 5; i++) {
            items.add(supporting("moderate" + i, EvidenceStrength.MODERATE, 0.7));
        }

        List<EvidenceItem> selected = selector.select(items);

        assertThat(selected).hasSize(Selection.MIN_SUPPORTING);
        assertThat(selected).extracting(EvidenceItem::id).contains("strong0");
    }

    /**
     * Reserved slots are not backing for the finding. A panel of contradictions with no
     * supporting entries reads as a refuted diagnosis, so the floor counts only support.
     */
    @Test
    void reservedContradictionSlotsDoNotSatisfyTheSupportingFloor() {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < Selection.MAX_REFUTING; i++) {
            items.add(refuting("r" + i));
        }
        for (int i = 0; i < 5; i++) {
            items.add(supporting("moderate" + i, EvidenceStrength.MODERATE, 0.7));
        }

        List<EvidenceItem> selected = selector.select(items);

        assertThat(selected).extracting(EvidenceItem::id)
            .filteredOn(id -> id.startsWith("moderate"))
            .hasSize(Selection.MIN_SUPPORTING);
        assertThat(selected).extracting(EvidenceItem::id).contains("r0");
    }

    @Test
    void returnsEmptyForNoEvidence() {
        assertThat(selector.select(null)).isEmpty();
        assertThat(selector.select(List.of())).isEmpty();
    }

    private EvidenceItem supporting(String id, EvidenceStrength strength, double confidence) {
        return item(id, EvidenceHypothesisAlignment.SUPPORTS, strength, confidence, Priority.PRIMARY);
    }

    private EvidenceItem refuting(String id) {
        return item(id, EvidenceHypothesisAlignment.REFUTES,
            EvidenceStrength.WEAK, 0.9, Priority.CONTEXTUAL);
    }

    private EvidenceItem item(
        String id,
        EvidenceHypothesisAlignment alignment,
        EvidenceStrength strength,
        double confidence,
        int priority
    ) {
        return EvidenceItem.builder()
            .id(id)
            .source("kubernetes")
            .type(EvidenceType.OTHER)
            .strength(strength)
            .evidenceHypothesisAlignment(alignment)
            .rawSnippet("snippet-" + id)
            .confidence(confidence)
            .priority(priority)
            .build();
    }
}
