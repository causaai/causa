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

    /**
     * The case the reservation exists for: enough decisive evidence to fill the panel twice
     * over. Without held-back slots the weaker items never appear at all.
     */
    @Test
    void weakerEvidenceAppearsEvenWhenDecisiveItemsCouldFillTheWholePanel() {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < Selection.MAX_UI_EVIDENCE * 2; i++) {
            items.add(supporting("definitive" + i, EvidenceStrength.DEFINITIVE, 0.99));
        }
        for (int i = 0; i < 5; i++) {
            items.add(supporting("moderate" + i, EvidenceStrength.MODERATE, 0.7));
        }

        List<EvidenceItem> selected = selector.select(items);

        assertThat(selected).hasSize(Selection.MAX_UI_EVIDENCE);
        assertThat(selected).extracting(EvidenceItem::id)
            .filteredOn(id -> id.startsWith("moderate"))
            .hasSize(Selection.WEAKER_SUPPORTING);
    }

    /** Held back, not handed over: weaker items take their slots and no more. */
    @Test
    void weakerEvidenceNeverClaimsMoreThanItsReservedSlots() {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(supporting("strong" + i, EvidenceStrength.STRONG, 0.9));
        }
        for (int i = 0; i < 10; i++) {
            items.add(supporting("moderate" + i, EvidenceStrength.MODERATE, 0.7));
        }

        List<EvidenceItem> selected = selector.select(items);

        assertThat(selected).hasSize(5 + Selection.WEAKER_SUPPORTING);
        assertThat(selected).extracting(EvidenceItem::id)
            .filteredOn(id -> id.startsWith("moderate"))
            .hasSize(Selection.WEAKER_SUPPORTING);
    }

    /** An empty weaker pool costs the panel nothing — the slots go back to decisive items. */
    @Test
    void unusedWeakerSlotsReturnToDecisiveEvidence() {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < Selection.MAX_UI_EVIDENCE * 2; i++) {
            items.add(supporting("definitive" + i, EvidenceStrength.DEFINITIVE, 0.99));
        }

        assertThat(selector.select(items)).hasSize(Selection.MAX_UI_EVIDENCE);
    }

    @Test
    void admitsWeakerEvidenceWhenTheFindingHasTooLittleSolidBacking() {
        List<EvidenceItem> items = new ArrayList<>();
        items.add(supporting("strong0", EvidenceStrength.STRONG, 0.9));
        for (int i = 0; i < 5; i++) {
            items.add(supporting("moderate" + i, EvidenceStrength.MODERATE, 0.7));
        }

        List<EvidenceItem> selected = selector.select(items);

        assertThat(selected).hasSize(1 + Selection.WEAKER_SUPPORTING);
        assertThat(selected).extracting(EvidenceItem::id).contains("strong0");
    }

    /**
     * Contradictions have their own reservation. Spending it must not come out of the slots
     * held for weaker supporting evidence — the two are counted separately.
     */
    @Test
    void reservedContradictionSlotsDoNotEatTheWeakerSupportingSlots() {
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
            .hasSize(Selection.WEAKER_SUPPORTING);
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
