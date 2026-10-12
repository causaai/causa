package com.causa.core.services.evidence;

import com.causa.core.domain.validation.EvidenceItem;

import java.util.List;

/**
 * Evidence Selector
 *
 * <p>The place where a full evidence harvest would be narrowed or ordered. Nothing is
 * narrowed or ordered today: every collected item is returned as collected, until the
 * ranking design is settled.
 *
 * @since 0.0.1
 */
public interface EvidenceSelector {

    /**
     * Returns the evidence to report for a finding.
     *
     * @param items all harvested evidence
     * @return every item, as collected, never null
     */
    List<EvidenceItem> select(List<EvidenceItem> items);
}
