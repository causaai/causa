package com.causa.core.services.evidence;

import com.causa.core.domain.validation.EvidenceItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Evidence Collection Result
 *
 * <p>The outcome of harvesting evidence for a single RCA finding: every
 * {@link EvidenceItem} produced, bound to the finding it was collected for.
 *
 * @param diagnosticId the diagnostic this evidence belongs to
 * @param items        every evidence item collected, in emission order
 *
 * @since 0.0.1
 */
public record EvidenceCollectionResult(
    String diagnosticId,
    List<EvidenceItem> items
) {

    public EvidenceCollectionResult {
        items = items == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * Returns an empty result for a diagnostic that produced no evidence.
     *
     * @param diagnosticId the diagnostic, may be null when there was no RCA at all
     * @return an empty collection result
     */
    public static EvidenceCollectionResult empty(String diagnosticId) {
        return new EvidenceCollectionResult(diagnosticId, List.of());
    }

    /** Total number of evidence items collected. */
    public int totalCount() {
        return items.size();
    }

    /**
     * Returns the distinct MCP servers that contributed evidence, sorted for stable logging.
     */
    public Set<String> contributingSources() {
        Set<String> sources = new TreeSet<>();
        for (EvidenceItem item : items) {
            if (item.source() != null) {
                sources.add(item.source());
            }
        }
        return Collections.unmodifiableSet(sources);
    }

    /** Returns true when no evidence at all could be collected for this finding. */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
