package com.causa.core.services.evidence.impl;

import com.causa.common.constants.EvidenceConstants.Selection;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceHypothesisAlignment;
import com.causa.core.domain.validation.EvidenceItem.EvidenceStrength;
import com.causa.core.services.evidence.EvidenceSelector;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Default Evidence Selector
 *
 * <p>Fills up to {@link Selection#MAX_UI_EVIDENCE} slots in priority order:
 *
 * <ol>
 *   <li>Slots reserved for the strongest contradicting items, if any exist. A diagnosis
 *       that the data partly argues against should say so on its face.</li>
 *   <li>The remainder goes to supporting evidence that is DEFINITIVE or STRONG, and stops
 *       there — an unfilled slot is not a problem to solve. Weaker items are admitted only
 *       to reach {@link Selection#MIN_SUPPORTING}, so a thin diagnosis still shows what it
 *       was decided on rather than reading as though it had been refuted.</li>
 * </ol>
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DefaultEvidenceSelector implements EvidenceSelector {

    /** Lower priority first, then higher confidence, then stronger evidence. */
    private static final Comparator<EvidenceItem> DISPLAY_ORDER =
        Comparator.comparingInt(EvidenceItem::priority)
            .thenComparing(Comparator.comparingDouble(EvidenceItem::confidence).reversed())
            .thenComparingInt(item -> item.strength() != null ? item.strength().ordinal() : Integer.MAX_VALUE);

    @Override
    public List<EvidenceItem> select(List<EvidenceItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Set<EvidenceItem> selected = new LinkedHashSet<>();

        take(selected, refuting(items), Selection.MAX_REFUTING);

        int reserved = selected.size();
        take(selected, strongSupporting(items), Selection.MAX_UI_EVIDENCE - reserved);

        // Measured against the supporting entries alone: reserved contradictions filling the
        // panel is not the same as the finding being backed up.
        int shortfall = Selection.MIN_SUPPORTING - (selected.size() - reserved);
        if (shortfall > 0) {
            take(selected, supporting(items), shortfall);
        }

        List<EvidenceItem> ordered = new ArrayList<>(selected);
        ordered.sort(DISPLAY_ORDER);
        return List.copyOf(ordered);
    }

    private void take(Set<EvidenceItem> selected, List<EvidenceItem> candidates, int limit) {
        int taken = 0;
        for (EvidenceItem candidate : candidates) {
            if (taken >= limit) {
                return;
            }
            if (selected.add(candidate)) {
                taken++;
            }
        }
    }

    private List<EvidenceItem> refuting(List<EvidenceItem> items) {
        return sorted(items, item ->
            item.evidenceHypothesisAlignment() == EvidenceHypothesisAlignment.REFUTES);
    }

    /** Solid enough to carry a claim on its own. */
    private boolean decisive(EvidenceItem item) {
        return item.strength() == EvidenceStrength.DEFINITIVE
            || item.strength() == EvidenceStrength.STRONG;
    }

    private List<EvidenceItem> strongSupporting(List<EvidenceItem> items) {
        return sorted(items, item ->
            item.evidenceHypothesisAlignment() == EvidenceHypothesisAlignment.SUPPORTS
                && decisive(item));
    }

    private List<EvidenceItem> supporting(List<EvidenceItem> items) {
        return sorted(items, item ->
            item.evidenceHypothesisAlignment() == EvidenceHypothesisAlignment.SUPPORTS);
    }

    private List<EvidenceItem> sorted(List<EvidenceItem> items, java.util.function.Predicate<EvidenceItem> filter) {
        return items.stream().filter(filter).sorted(DISPLAY_ORDER).toList();
    }
}
