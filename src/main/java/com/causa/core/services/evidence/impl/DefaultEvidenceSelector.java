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
 *   <li>{@link Selection#WEAKER_SUPPORTING} further slots held back for MODERATE and weaker
 *       supporting items, so the panel shows some of the softer observations behind the
 *       finding and not only the facts that settle it.</li>
 *   <li>Everything else goes to supporting evidence that is DEFINITIVE or STRONG. Slots the
 *       weaker items leave unused come back here, so holding them back never costs the panel
 *       a solid entry — but an unfilled slot is still not a problem to solve.</li>
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
        take(selected, strongSupporting(items),
            Selection.MAX_UI_EVIDENCE - reserved - Selection.WEAKER_SUPPORTING);

        take(selected, weakerSupporting(items), Selection.WEAKER_SUPPORTING);

        // Slots the weaker items did not use are not left empty.
        take(selected, strongSupporting(items), Selection.MAX_UI_EVIDENCE - selected.size());

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

    private List<EvidenceItem> weakerSupporting(List<EvidenceItem> items) {
        return sorted(items, item ->
            item.evidenceHypothesisAlignment() == EvidenceHypothesisAlignment.SUPPORTS
                && !decisive(item));
    }

    private List<EvidenceItem> sorted(List<EvidenceItem> items, java.util.function.Predicate<EvidenceItem> filter) {
        return items.stream().filter(filter).sorted(DISPLAY_ORDER).toList();
    }
}
