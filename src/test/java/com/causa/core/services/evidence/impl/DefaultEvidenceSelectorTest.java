package com.causa.core.services.evidence.impl;

import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.domain.validation.EvidenceItem.EvidenceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The selected panel is what the API renders. Until the ranking design lands it renders the
 * whole harvest: no cap, no reordering, nothing dropped.
 */
class DefaultEvidenceSelectorTest {

    private final DefaultEvidenceSelector selector = new DefaultEvidenceSelector();

    @Test
    void keepsEveryItemInHarvestOrder() {
        List<EvidenceItem> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(item("s" + i, EvidenceStrength.CIRCUMSTANTIAL, 0.1));
        }
        items.add(item("r", EvidenceStrength.DEFINITIVE, 0.99));

        assertThat(selector.select(items)).containsExactlyElementsOf(items);
    }

    @Test
    void keepsRepeatsOfTheSameSnippet() {
        EvidenceItem first = item("a", EvidenceStrength.STRONG, 0.8);
        EvidenceItem repeat = item("a", EvidenceStrength.STRONG, 0.8);

        assertThat(selector.select(List.of(first, repeat))).hasSize(2);
    }

    @Test
    void returnsEmptyForNoEvidence() {
        assertThat(selector.select(null)).isEmpty();
        assertThat(selector.select(List.of())).isEmpty();
    }

    private EvidenceItem item(String id, EvidenceStrength strength, double confidence) {
        return EvidenceItem.builder()
            .id(id)
            .source("kubernetes")
            .type(EvidenceType.OTHER)
            .strength(strength)
            .rawSnippet("snippet-" + id)
            .confidence(confidence)
            .build();
    }
}
