package com.causa.core.services.validation.impl;

import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.validation.Assertion;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.services.validation.EvidenceMatcher;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Context-based evidence matcher using keyword and pattern matching.
 *
 * <p>Searches diagnostic context for evidence matching assertion claims.
 * Uses keyword matching, pattern recognition, and contextual relevance scoring.
 *
 * <p>Future enhancement: Use semantic search with embeddings for better matching.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ContextBasedEvidenceMatcher implements EvidenceMatcher {

    private static final CausaLogger log = CausaLogger.getLogger(ContextBasedEvidenceMatcher.class);

    // Context section markers
    private static final Pattern SECTION_PATTERN = Pattern.compile("^---\\s*(.+?)\\s*---$", Pattern.MULTILINE);

    // Common evidence patterns
    private static final Map<String, Pattern> EVIDENCE_PATTERNS = Map.ofEntries(
        Map.entry("oomkilled", Pattern.compile("(?i)(oomkilled|oom\\s*killed|exit\\s*code\\s*137|reason:\\s*oomkilled)", Pattern.CASE_INSENSITIVE)),
        Map.entry("memory_limit", Pattern.compile("(?i)memory\\s*limit[:\\s]*(\\d+\\s*[kmg]i?b?)", Pattern.CASE_INSENSITIVE)),
        Map.entry("memory_usage", Pattern.compile("(?i)memory\\s*usage[:\\s]*(\\d+(?:\\.\\d+)?\\s*[kmg]i?b?)", Pattern.CASE_INSENSITIVE)),
        Map.entry("heap_usage", Pattern.compile("(?i)heap\\s*usage[:\\s]*(\\d+(?:\\.\\d+)?\\s*[kmg]i?b?)", Pattern.CASE_INSENSITIVE)),
        Map.entry("cpu_usage", Pattern.compile("(?i)cpu\\s*usage[:\\s]*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE)),
        Map.entry("restart_count", Pattern.compile("(?i)restart\\s*count[:\\s]*(\\d+)", Pattern.CASE_INSENSITIVE)),
        Map.entry("pod_status", Pattern.compile("(?i)status[:\\s]*(running|pending|failed|crashloopbackoff|oomkilled)", Pattern.CASE_INSENSITIVE)),
        Map.entry("gc_activity", Pattern.compile("(?i)(gc|garbage\\s*collection)[:\\s]*([^\\n]+)", Pattern.CASE_INSENSITIVE))
    );

    @Override
    public List<Evidence> findEvidence(Assertion assertion, String diagnosticContext) {
        log.debug("Finding evidence for assertion")
            .field("assertionId", assertion.id())
            .field("assertionType", assertion.type())
            .log();

        List<Evidence> evidence = new ArrayList<>();

        // Parse context into sections
        Map<String, String> sections = parseContextSections(diagnosticContext);

        // Search for evidence based on assertion type
        switch (assertion.type()) {
            case OBSERVATION -> evidence.addAll(findObservationEvidence(assertion, sections));
            case TREND -> evidence.addAll(findTrendEvidence(assertion, sections));
            case CAUSALITY -> evidence.addAll(findCausalityEvidence(assertion, sections));
            case CONFIGURATION -> evidence.addAll(findConfigurationEvidence(assertion, sections));
            case RECOMMENDATION -> {
                // Recommendations don't need evidence validation
                log.debug("Skipping evidence search for recommendation assertion");
            }
        }

        // Keyword-based search across all sections
        evidence.addAll(findKeywordMatches(assertion, diagnosticContext));

        log.debug("Evidence search completed")
            .field("assertionId", assertion.id())
            .field("evidenceFound", evidence.size())
            .log();

        return evidence;
    }

    @Override
    public Map<String, List<Evidence>> findEvidenceForAll(
        List<Assertion> assertions,
        String diagnosticContext
    ) {
        log.info("Finding evidence for all assertions")
            .field("totalAssertions", assertions.size())
            .log();

        Map<String, List<Evidence>> evidenceMap = new HashMap<>();

        for (Assertion assertion : assertions) {
            List<Evidence> evidence = findEvidence(assertion, diagnosticContext);
            evidenceMap.put(assertion.id(), evidence);
        }

        log.info("Evidence search completed for all assertions")
            .field("totalAssertions", assertions.size())
            .field("assertionsWithEvidence", evidenceMap.values().stream().filter(list -> !list.isEmpty()).count())
            .log();

        return evidenceMap;
    }

    /**
     * Parses diagnostic context into named sections.
     */
    private Map<String, String> parseContextSections(String context) {
        Map<String, String> sections = new HashMap<>();

        String[] lines = context.split("\n");
        String currentSection = "general";
        StringBuilder sectionContent = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = SECTION_PATTERN.matcher(line);
            if (matcher.matches()) {
                // Save previous section
                if (sectionContent.length() > 0) {
                    sections.put(currentSection, sectionContent.toString().trim());
                    sectionContent = new StringBuilder();
                }
                // Start new section
                currentSection = matcher.group(1).toLowerCase().replaceAll("\\s+", "_");
            } else {
                sectionContent.append(line).append("\n");
            }
        }

        // Save last section
        if (sectionContent.length() > 0) {
            sections.put(currentSection, sectionContent.toString().trim());
        }

        return sections;
    }

    /**
     * Finds evidence for observation assertions.
     */
    private List<Evidence> findObservationEvidence(
        Assertion assertion,
        Map<String, String> sections
    ) {
        List<Evidence> evidence = new ArrayList<>();
        String text = assertion.text().toLowerCase();

        // Check for OOMKilled
        if (text.contains("oomkill") || text.contains("oom")) {
            evidence.addAll(findOOMKilledEvidence(sections));
        }

        // Check for pod status
        if (text.contains("running") || text.contains("failed") || text.contains("pending")) {
            evidence.addAll(findPodStatusEvidence(sections, text));
        }

        // Check for restart count
        if (text.contains("restart")) {
            evidence.addAll(findRestartEvidence(sections));
        }

        return evidence;
    }

    /**
     * Finds evidence for trend assertions.
     */
    private List<Evidence> findTrendEvidence(
        Assertion assertion,
        Map<String, String> sections
    ) {
        List<Evidence> evidence = new ArrayList<>();
        String text = assertion.text().toLowerCase();

        // Check for memory trends
        if (text.contains("memory") && (text.contains("increase") || text.contains("decrease"))) {
            evidence.addAll(findMemoryTrendEvidence(sections));
        }

        // Check for heap trends
        if (text.contains("heap") && (text.contains("increase") || text.contains("grow"))) {
            evidence.addAll(findHeapTrendEvidence(sections));
        }

        return evidence;
    }

    /**
     * Finds evidence for causality assertions.
     */
    private List<Evidence> findCausalityEvidence(
        Assertion assertion,
        Map<String, String> sections
    ) {
        // Causality is complex - look for both cause and effect evidence
        List<Evidence> evidence = new ArrayList<>();
        String text = assertion.text().toLowerCase();

        // OOMKill caused by heap exhaustion
        if (text.contains("oomkill") && text.contains("heap")) {
            evidence.addAll(findOOMKilledEvidence(sections));
            evidence.addAll(findHeapTrendEvidence(sections));
        }

        return evidence;
    }

    /**
     * Finds evidence for configuration assertions.
     */
    private List<Evidence> findConfigurationEvidence(
        Assertion assertion,
        Map<String, String> sections
    ) {
        List<Evidence> evidence = new ArrayList<>();
        String text = assertion.text().toLowerCase();

        // Memory limit configuration
        if (text.contains("memory") && text.contains("limit")) {
            evidence.addAll(findMemoryLimitEvidence(sections));
        }

        return evidence;
    }

    /**
     * Finds OOMKilled evidence.
     */
    private List<Evidence> findOOMKilledEvidence(Map<String, String> sections) {
        List<Evidence> evidence = new ArrayList<>();

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            String sectionName = entry.getKey();
            String content = entry.getValue();

            Matcher matcher = EVIDENCE_PATTERNS.get("oomkilled").matcher(content);
            if (matcher.find()) {
                String snippet = extractSnippet(content, matcher.start(), 100);
                evidence.add(Evidence.of(
                    sectionName,
                    Evidence.EvidenceType.KUBERNETES_EVENT,
                    snippet,
                    0.95 // High relevance
                ));
            }
        }

        return evidence;
    }

    /**
     * Finds pod status evidence.
     */
    private List<Evidence> findPodStatusEvidence(Map<String, String> sections, String targetStatus) {
        List<Evidence> evidence = new ArrayList<>();

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            String sectionName = entry.getKey();
            String content = entry.getValue();

            if (content.toLowerCase().contains("status") && content.toLowerCase().contains(targetStatus)) {
                Matcher matcher = EVIDENCE_PATTERNS.get("pod_status").matcher(content);
                if (matcher.find()) {
                    String snippet = extractSnippet(content, matcher.start(), 80);
                    evidence.add(Evidence.of(
                        sectionName,
                        Evidence.EvidenceType.KUBERNETES_EVENT,
                        snippet,
                        0.90
                    ));
                }
            }
        }

        return evidence;
    }

    /**
     * Finds restart evidence.
     */
    private List<Evidence> findRestartEvidence(Map<String, String> sections) {
        List<Evidence> evidence = new ArrayList<>();

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            String sectionName = entry.getKey();
            String content = entry.getValue();

            Matcher matcher = EVIDENCE_PATTERNS.get("restart_count").matcher(content);
            if (matcher.find()) {
                String snippet = extractSnippet(content, matcher.start(), 80);
                evidence.add(Evidence.of(
                    sectionName,
                    Evidence.EvidenceType.KUBERNETES_EVENT,
                    snippet,
                    0.85
                ));
            }
        }

        return evidence;
    }

    /**
     * Finds memory trend evidence.
     */
    private List<Evidence> findMemoryTrendEvidence(Map<String, String> sections) {
        List<Evidence> evidence = new ArrayList<>();

        // Look for memory usage patterns
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (entry.getKey().contains("metric") || entry.getKey().contains("prometheus")) {
                String content = entry.getValue();
                Matcher matcher = EVIDENCE_PATTERNS.get("memory_usage").matcher(content);

                List<String> memoryValues = new ArrayList<>();
                while (matcher.find()) {
                    memoryValues.add(matcher.group(1));
                }

                if (memoryValues.size() >= 2) {
                    String snippet = "Memory values: " + String.join(", ", memoryValues);
                    evidence.add(Evidence.of(
                        entry.getKey(),
                        Evidence.EvidenceType.METRIC,
                        snippet,
                        0.80
                    ));
                }
            }
        }

        return evidence;
    }

    /**
     * Finds heap trend evidence.
     */
    private List<Evidence> findHeapTrendEvidence(Map<String, String> sections) {
        List<Evidence> evidence = new ArrayList<>();

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (entry.getKey().contains("jfr") || entry.getKey().contains("memory") || entry.getKey().contains("cryostat")) {
                String content = entry.getValue();
                Matcher matcher = EVIDENCE_PATTERNS.get("heap_usage").matcher(content);

                List<String> heapValues = new ArrayList<>();
                while (matcher.find()) {
                    heapValues.add(matcher.group(1));
                }

                if (heapValues.size() >= 2) {
                    String snippet = "Heap values: " + String.join(", ", heapValues);
                    evidence.add(Evidence.of(
                        entry.getKey(),
                        Evidence.EvidenceType.MEMORY_ANALYSIS,
                        snippet,
                        0.85
                    ));
                }
            }
        }

        return evidence;
    }

    /**
     * Finds memory limit evidence.
     */
    private List<Evidence> findMemoryLimitEvidence(Map<String, String> sections) {
        List<Evidence> evidence = new ArrayList<>();

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            String content = entry.getValue();
            Matcher matcher = EVIDENCE_PATTERNS.get("memory_limit").matcher(content);

            if (matcher.find()) {
                String snippet = extractSnippet(content, matcher.start(), 80);
                evidence.add(Evidence.of(
                    entry.getKey(),
                    Evidence.EvidenceType.KUBERNETES_EVENT,
                    snippet,
                    0.90
                ));
            }
        }

        return evidence;
    }

    /**
     * Finds evidence using keyword matching.
     */
    private List<Evidence> findKeywordMatches(Assertion assertion, String context) {
        List<Evidence> evidence = new ArrayList<>();

        // Extract key terms from assertion (simple approach)
        String[] words = assertion.text().toLowerCase().split("\\W+");
        List<String> keywords = Arrays.stream(words)
            .filter(w -> w.length() > 3) // Skip short words
            .filter(w -> !isStopWord(w))
            .collect(Collectors.toList());

        if (keywords.isEmpty()) {
            return evidence;
        }

        // Search context for keyword matches
        String[] contextLines = context.split("\n");
        for (int i = 0; i < contextLines.length; i++) {
            String line = contextLines[i];
            String lowerLine = line.toLowerCase();

            int matchCount = (int) keywords.stream()
                .filter(lowerLine::contains)
                .count();

            // If multiple keywords match, this line is relevant
            if (matchCount >= Math.min(2, keywords.size())) {
                double relevance = Math.min(0.70, 0.40 + (matchCount * 0.10));
                evidence.add(Evidence.of(
                    "keyword-match",
                    Evidence.EvidenceType.OTHER,
                    line.trim(),
                    relevance
                ));
            }
        }

        return evidence;
    }

    /**
     * Extracts a snippet around a position.
     */
    private String extractSnippet(String text, int position, int maxLength) {
        int start = Math.max(0, position - maxLength / 2);
        int end = Math.min(text.length(), position + maxLength / 2);

        String snippet = text.substring(start, end).trim();

        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }

    /**
     * Checks if a word is a stop word.
     */
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
            "the", "and", "this", "that", "with", "from", "have", "has",
            "was", "were", "been", "being", "are", "for", "not", "but"
        );
        return stopWords.contains(word);
    }
}
