package com.causa.core.services.evidence.impl;

import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.services.evidence.EvidenceSelector;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Default Evidence Selector
 *
 * <p>Selects nothing: the whole harvest is returned, in the order it was collected. The budget
 * that reserved slots for contradictions and weaker observations is gone along with the cap —
 * ranking is being redesigned, and until it lands a partial order would drop items on criteria
 * that are not yet settled.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class DefaultEvidenceSelector implements EvidenceSelector {

    @Override
    public List<EvidenceItem> select(List<EvidenceItem> items) {
        return items == null ? List.of() : List.copyOf(items);
    }
}
