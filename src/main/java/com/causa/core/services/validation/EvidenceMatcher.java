package com.causa.core.services.validation;

import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.Evidence;

import java.util.List;
import java.util.Map;

/**
 * Finds evidence in the diagnostic context that supports or refutes assertions.
 *
 * <p>Searches through the collected MCP context (Kubernetes events, pod logs,
 * Prometheus metrics, etc.) to find relevant passages for each assertion.
 *
 * @since 0.0.1
 */
public interface EvidenceMatcher {

    /**
     * Finds evidence for a single assertion.
     *
     * @param assertion the assertion to find evidence for
     * @param diagnosticContext the collected MCP context as a string
     * @return list of evidence found, ordered by relevance score descending
     */
    List<Evidence> findEvidence(Assertion assertion, String diagnosticContext);

    /**
     * Finds evidence for all assertions in a single pass over the context.
     *
     * @param assertions the list of assertions to find evidence for
     * @param diagnosticContext the collected MCP context as a string
     * @return map from assertion ID to its list of evidence pieces
     */
    Map<String, List<Evidence>> findEvidenceForAll(List<Assertion> assertions, String diagnosticContext);
}
