package com.causa.core.services.evidence;

import com.causa.core.domain.validation.EvidenceItem;

import java.util.List;

/**
 * Evidence Selector
 *
 * <p>Narrows a full evidence harvest down to the handful a developer should actually read.
 *
 * <p>A harvest runs to dozens of items — every assertion the LLM checked, every rule the
 * engine evaluated. Nearly all of it is corroboration of the same few facts. Selection picks
 * the strongest distinct ones and, deliberately, keeps room for the two kinds of evidence a
 * "top N by confidence" ranking would always drop: what <em>contradicts</em> the finding, and
 * what could not be obtained at all.
 *
 * @since 0.0.1
 */
public interface EvidenceSelector {

    /**
     * Selects the user-facing subset of collected evidence.
     *
     * @param items all harvested evidence
     * @return the selected items in display order, never null, at most the configured maximum
     */
    List<EvidenceItem> select(List<EvidenceItem> items);
}
