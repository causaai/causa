package com.causa.core.services.validation;

import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.Assertion;

import java.util.List;

/**
 * Extracts atomic assertions from RCA output.
 *
 * <p>Breaks down the LLM-generated RCA into individual verifiable claims
 * that can be validated independently against the diagnostic context.
 *
 * @since 0.0.1
 */
public interface AssertionExtractor {

    /**
     * Extracts all assertions from an RCA.
     *
     * @param rca the root cause analysis
     * @return list of extracted assertions
     */
    List<Assertion> extractAssertions(RootCauseAnalysis rca);

    /**
     * Extracts assertions from a specific text field.
     *
     * @param text the text to extract from
     * @param source the source of this text in the RCA
     * @param relatedField optional field name for traceability
     * @return list of extracted assertions
     */
    List<Assertion> extractFromText(
        String text,
        Assertion.AssertionSource source,
        String relatedField
    );
}