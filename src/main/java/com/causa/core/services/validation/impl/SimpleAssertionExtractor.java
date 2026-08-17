package com.causa.core.services.validation.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.services.validation.AssertionExtractor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Simple rule-based assertion extractor.
 *
 * <p>Splits RCA text into atomic claims using sentence boundaries and
 * categorizes them based on keywords and patterns.
 *
 * <p>Enabled when: causa.validation.assertion-extractor=simple (default)
 *
 * <p>Alternative: Use LlmAssertionExtractor for more sophisticated extraction.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class SimpleAssertionExtractor implements AssertionExtractor {

    private static final CausaLogger log = CausaLogger.getLogger(SimpleAssertionExtractor.class);

    // Sentence splitter (simple approach - can be enhanced)
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s+");

    // Keywords for assertion type classification
    private static final List<String> CAUSAL_KEYWORDS = List.of(
        "because", "caused by", "due to", "resulted in", "led to", "triggered by"
    );

    private static final List<String> TREND_KEYWORDS = List.of(
        "increased", "decreased", "growing", "declining", "gradually", "continuously",
        "steadily", "over time", "trending"
    );

    private static final List<String> CONFIG_KEYWORDS = List.of(
        "limit", "set to", "configured", "allocated", "reserved", "quota"
    );

    private static final List<String> RECOMMENDATION_KEYWORDS = List.of(
        "should", "recommend", "increase", "decrease", "adjust", "modify",
        "update", "change", "consider", "suggest"
    );

    @Override
    public List<Assertion> extractAssertions(RootCauseAnalysis rca) {
        log.debug("Extracting assertions from RCA")
            .field("issueTitle", rca.issueTitle())
            .log();

        List<Assertion> assertions = new ArrayList<>();

        // Extract from root cause
        if (rca.rootCause() != null && !rca.rootCause().isBlank()) {
            assertions.addAll(extractFromText(
                rca.rootCause(),
                Assertion.AssertionSource.ROOT_CAUSE,
                "rootCause"
            ));
        }

        // Extract from issue description
        if (rca.issueDescription() != null && !rca.issueDescription().isBlank()) {
            assertions.addAll(extractFromText(
                rca.issueDescription(),
                Assertion.AssertionSource.ISSUE_DESCRIPTION,
                "issueDescription"
            ));
        }

        // Extract from technical description
        if (rca.technicalDescription() != null && !rca.technicalDescription().isBlank()) {
            assertions.addAll(extractFromText(
                rca.technicalDescription(),
                Assertion.AssertionSource.TECHNICAL_DESCRIPTION,
                "technicalDescription"
            ));
        }

        // Extract from recommendations
        if (rca.recommendations() != null && !rca.recommendations().isEmpty()) {
            for (int i = 0; i < rca.recommendations().size(); i++) {
                var recommendation = rca.recommendations().get(i);
                assertions.addAll(extractFromText(
                    recommendation.solutionTitle(),
                    Assertion.AssertionSource.POSSIBLE_SOLUTIONS,
                    "recommendations[" + i + "]"
                ));
            }
        }

        log.info("Assertions extracted")
            .field("totalAssertions", assertions.size())
            .field("fromRootCause", countBySource(assertions, Assertion.AssertionSource.ROOT_CAUSE))
            .field("fromIssueDesc", countBySource(assertions, Assertion.AssertionSource.ISSUE_DESCRIPTION))
            .field("fromTechnicalDesc", countBySource(assertions, Assertion.AssertionSource.TECHNICAL_DESCRIPTION))
            .field("fromSolutions", countBySource(assertions, Assertion.AssertionSource.POSSIBLE_SOLUTIONS))
            .log();

        return assertions;
    }

    @Override
    public List<Assertion> extractFromText(
        String text,
        Assertion.AssertionSource source,
        String relatedField
    ) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Split into sentences
        List<String> sentences = splitIntoSentences(text);

        // Convert each sentence into an assertion
        return sentences.stream()
            .filter(s -> !s.isBlank())
            .map(sentence -> createAssertion(sentence, source, relatedField))
            .collect(Collectors.toList());
    }

    /**
     * Splits text into sentences.
     */
    private List<String> splitIntoSentences(String text) {
        // Simple sentence splitter
        String[] parts = SENTENCE_PATTERN.split(text);
        return Arrays.stream(parts)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    /**
     * Creates an assertion from a sentence.
     */
    private Assertion createAssertion(
        String sentence,
        Assertion.AssertionSource source,
        String relatedField
    ) {
        String id = generateAssertionId(sentence);
        Assertion.AssertionType type = classifyAssertion(sentence, source);

        return Assertion.of(id, sentence, type, source);
    }

    /**
     * Classifies assertion type based on keywords and patterns.
     */
    private Assertion.AssertionType classifyAssertion(
        String text,
        Assertion.AssertionSource source
    ) {
        String lowerText = text.toLowerCase();

        // Recommendations from solutions
        if (source == Assertion.AssertionSource.POSSIBLE_SOLUTIONS) {
            return Assertion.AssertionType.RECOMMENDATION;
        }

        // Check for recommendation keywords
        if (containsAny(lowerText, RECOMMENDATION_KEYWORDS)) {
            return Assertion.AssertionType.RECOMMENDATION;
        }

        // Check for causal relationships
        if (containsAny(lowerText, CAUSAL_KEYWORDS)) {
            return Assertion.AssertionType.CAUSALITY;
        }

        // Check for trends
        if (containsAny(lowerText, TREND_KEYWORDS)) {
            return Assertion.AssertionType.TREND;
        }

        // Check for configuration
        if (containsAny(lowerText, CONFIG_KEYWORDS)) {
            return Assertion.AssertionType.CONFIGURATION;
        }

        // Default to observation
        return Assertion.AssertionType.OBSERVATION;
    }

    /**
     * Checks if text contains any of the keywords.
     */
    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    /**
     * Generates a unique assertion ID.
     */
    private String generateAssertionId(String text) {
        // Use first 8 chars of UUID + text hash for uniqueness
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        int textHash = Math.abs(text.hashCode() % 10000);
        return String.format("assert-%s-%04d", uuid, textHash);
    }

    /**
     * Counts assertions by source.
     */
    private long countBySource(List<Assertion> assertions, Assertion.AssertionSource source) {
        return assertions.stream()
            .filter(a -> a.source() == source)
            .count();
    }
}
