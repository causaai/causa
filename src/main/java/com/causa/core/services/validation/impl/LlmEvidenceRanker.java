package com.causa.core.services.validation.impl;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.causa.common.constants.ConfigConstants;
import com.causa.common.constants.LLMConstants;
import com.causa.common.constants.PromptConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.domain.validation.ValidationEvidence;
import com.causa.core.domain.validation.EvidenceRankingRequest.RcaSummary;
import com.causa.core.domain.validation.EvidenceRankingResponse;
import com.causa.core.ports.llm.PromptSender;
import com.causa.core.services.PromptTemplateLoader;
import com.causa.core.services.validation.EvidenceRanker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Evidence ranking and deduplication with smart routing optimization.
 *
 * <p>Ranks all collected evidence from both paths — PATH A (assertion validation) and PATH B
 * (rule engine) — as a single pool. Nothing bypasses ranking: the harvested evidence is handed
 * here after validation status and score are finalized, and the ranked result is the final set.
 *
 * <p>Every non-empty set goes through the LLM so duplicates are merged before ranking — a set of
 * two identical facts is exactly where deduplication matters, so there is no item-count shortcut.
 * The deterministic sort by {@code relevanceScore} survives only as the fallback when the LLM call
 * fails.
 *
 * <p>Division of labour: the LLM only deduplicates and assigns a per-item RCA <em>relevance</em>
 * score. The final ordering — {@code finalScore = 0.2 × strength + 0.8 × relevance}, sorted and
 * capped at {@value #MAX_RANKED_ITEMS} — is computed here in code (see {@link #computeRanking}).
 * The model does not reliably apply that arithmetic, so keeping it in code makes strength
 * authoritative and the ranking deterministic.
 *
 * <p>Note: relevanceScore (the "strength" term) is set during assertion validation by LLM or
 * rule-based matcher.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LlmEvidenceRanker implements EvidenceRanker {

    private static final CausaLogger log = CausaLogger.getLogger(LlmEvidenceRanker.class);
    private static final int MAX_RANKED_ITEMS = 20;

    // Final ranking score = STRENGTH_WEIGHT × strength + RELEVANCE_WEIGHT × relevance.
    // The LLM supplies relevance only; this arithmetic runs in code (see computeRanking) so
    // strength provably counts rather than being left to the model, which does not apply it.
    //
    // Weights: relevance leads, strength refines. Relevance is quantized to tiers 0.25 apart, so
    // one tier gap is 0.8 × 0.25 = 0.20 — exactly the full strength swing (0.2 × 1.0). Strength
    // therefore orders items WITHIN a relevance tier and can reach, but never vault past, the next
    // tier. That keeps "closeness to root cause" the primary ordering principle while strength stays
    // a meaningful secondary (a 0.9-vs-0.1 strength gap is a 0.16 spread within a tier).
    private static final double STRENGTH_WEIGHT = 0.2;
    private static final double RELEVANCE_WEIGHT = 0.8;

    private final PromptSender promptSender;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LlmConfigCache llmConfigCache;

    @Inject
    public LlmEvidenceRanker(
            PromptSender promptSender,
            ObjectMapper objectMapper,
            LlmConfigCache llmConfigCache) {
        this.promptSender = promptSender;
        this.objectMapper = objectMapper;
        this.llmConfigCache = llmConfigCache;
        this.promptTemplateLoader = new PromptTemplateLoader(PromptConstants.TEMPLATE_PATH_EVIDENCE_RANKING);
    }

    @Override
    public EvidenceRankingResponse rankAndDeduplicate(
            RcaSummary rca,
            List<ValidationEvidence> evidenceList,
            double validationScore,
            String validationStatus) {

        // Filter evidence with statements only
        List<ValidationEvidence> statementEvidence = evidenceList.stream()
            .filter(ev -> ev.statement() != null && !ev.statement().isBlank())
            .toList();

        log.info("Ranking evidence")
            .field("totalEvidence", evidenceList.size())
            .field("statementEvidence", statementEvidence.size())
            .log();

        // Log input evidence details for verification
        log.info("📥 INPUT EVIDENCE FOR RANKING")
            .field("count", statementEvidence.size())
            .log();

        statementEvidence.forEach(ev -> {
            log.info("  • Evidence")
                .field("id", ev.id())
                .field("strength", String.format("%.2f", ev.relevanceScore()))
                .field("statement", truncate(ev.statement(), 100))
                .log();
        });

        // Nothing to rank — return empty rather than calling the LLM with no evidence.
        if (statementEvidence.isEmpty()) {
            return new EvidenceRankingResponse(List.of(), "No evidence to rank");
        }

        // Every non-empty set is deduplicated and ranked by the LLM — no item-count shortcut,
        // since a pair of identical facts is exactly where deduplication matters.
        return rankWithLlm(rca, statementEvidence, validationScore, validationStatus);
    }

    /**
     * Runs the LLM ranking path: build prompt → call model → parse, falling back to a deterministic
     * sort by {@code relevanceScore} if the call or parse fails. Invoked for every non-empty set by
     * {@link #rankAndDeduplicate}.
     */
    private EvidenceRankingResponse rankWithLlm(
            RcaSummary rca,
            List<ValidationEvidence> evidenceList,
            double validationScore,
            String validationStatus) {

        try {
            String provider = resolveProvider();
            PromptTemplateLoader.PromptTemplate template = promptTemplateLoader.loadTemplate(provider, "");
            String compactPrompt = buildCompactPrompt(template, rca, evidenceList, validationScore, validationStatus);

            // Record exactly which prompt was resolved — proof of identity, so a provider mismatch or
            // silent fallback can never be mistaken for a passing run.
            log.info("Evidence ranking prompt selected")
                .field("name", template.name())
                .field("version", template.version())
                .field("provider", provider)
                .log();

            log.info("Calling LLM for evidence ranking")
                .field("evidenceCount", evidenceList.size())
                .log();

            // Send via the PromptSender port so ranking works for every configured provider —
            // Bob (shell) as well as Anthropic/Vertex — rather than only the providers
            // ChatModelFactory can build. Skills are disabled: ranking is a pure scoring call
            // and must not trigger the tool/skill loop.
            LLMRequest request = LLMRequest.builder(compactPrompt)
                .systemPrompt(template.systemPrompt())
                .enableSkills(false)
                .build();

            LLMResponse llmResponse = promptSender.send(request);
            String responseText = llmResponse.responseText();
            if (responseText == null || responseText.isBlank()) {
                // Treat an empty response like a parse failure — route to the deterministic fallback.
                throw new IllegalStateException("LLM returned an empty ranking response");
            }

            log.info("LLM response received")
                .field("responseLength", responseText.length())
                .field("responsePreview", responseText.substring(0, Math.min(200, responseText.length())))
                .log();

            Map<String, Double> relevanceById = parseRelevanceScores(responseText);
            List<String> rankedIds = computeRanking(evidenceList, relevanceById, MAX_RANKED_ITEMS);
            EvidenceRankingResponse rankingResponse = sanitize(
                new EvidenceRankingResponse(rankedIds, "LLM relevance scoring + code-computed ranking"),
                evidenceList);

            // Log output ranking results
            logRankingResults(evidenceList, rankingResponse, "LLM");

            return rankingResponse;

        } catch (Exception e) {
            log.warn("LLM ranking failed, falling back to deterministic ranking")
                .exception(e)
                .log();

            // Fallback to deterministic ranking
            List<String> rankedIds = evidenceList.stream()
                .sorted(Comparator.comparingDouble(ValidationEvidence::relevanceScore).reversed())
                .limit(MAX_RANKED_ITEMS)
                .map(ValidationEvidence::id)
                .toList();

            EvidenceRankingResponse fallbackResponse = new EvidenceRankingResponse(rankedIds, "Fallback ranking (LLM failed)");
            logRankingResults(evidenceList, fallbackResponse, "Fallback (LLM failed)");
            return fallbackResponse;
        }
    }

    private String resolveProvider() {
        return llmConfigCache.getActive()
            .map(config -> toTemplateProvider(config.getProvider()))
            .orElse(LLMConstants.Provider.ANTHROPIC);
    }

    /**
     * Maps the stored provider enum onto the provider id used in the prompt template YAML.
     *
     * <p>VERTEX_AI serves Anthropic models on Vertex, so it must resolve to the
     * {@code "vertex-ai-anthropic"} template entry — the bare enum name ({@code "vertex-ai"}) matches
     * nothing in the template and would silently force the default fallback.
     */
    private static String toTemplateProvider(ConfigConstants.LlmProvider provider) {
        return switch (provider) {
            case VERTEX_AI -> LLMConstants.Provider.VERTEX_AI_ANTHROPIC;
            case ANTHROPIC -> LLMConstants.Provider.ANTHROPIC;
            case BOB -> LLMConstants.Provider.IBM_BOB;
        };
    }

    private String buildCompactPrompt(
            PromptTemplateLoader.PromptTemplate template,
            RcaSummary rca,
            List<ValidationEvidence> evidenceList,
            double validationScore,
            String validationStatus) {

        // Evidence format: "ID: statement [strength: score]"
        // Strength = assertion-level relevanceScore (used in formula: 0.2 × strength + 0.8 × relevance)
        String evidenceCompact = evidenceList.stream()
            .map(ev -> String.format("%s: %s [strength: %.2f]", ev.id(), ev.statement(), ev.relevanceScore()))
            .collect(Collectors.joining("\n"));

        return template.userPrompt()
            .replace("{{rca_summary}}", truncate(rca.rootCause(), 150))
            .replace("{{validation_status}}", validationStatus)
            .replace("{{validation_score}}", String.format("%.0f", validationScore * 100))
            .replace("{{evidence_compact}}", evidenceCompact);
    }

    /**
     * Parses the LLM response into a map of evidence id → RCA relevance score (0.0–1.0).
     *
     * <p>The LLM only deduplicates and scores relevance; it neither sorts nor caps. Items it merged
     * as duplicates are simply absent from the returned map. The final ranking is computed from this
     * map in {@link #computeRanking}. Insertion order is preserved (a {@link LinkedHashMap}) so the
     * sort is a stable reordering of what the model returned.
     */
    private Map<String, Double> parseRelevanceScores(String response) throws JsonProcessingException {
        // Extract JSON from response - robust handling of markdown, mixed text, and embedded JSON
        String jsonStr = response.trim();

        log.info("Parsing LLM response")
            .field("responseLength", response.length())
            .field("startsWithBrace", jsonStr.startsWith("{"))
            .log();

        // Strategy 1: Handle markdown code blocks
        if (jsonStr.contains("```")) {
            int start = jsonStr.indexOf("```json");
            if (start == -1) start = jsonStr.indexOf("```");
            if (start != -1) {
                int end = jsonStr.indexOf("```", start + 3);
                if (end != -1) {
                    jsonStr = jsonStr.substring(start, end)
                        .replaceFirst("```json\\s*", "")
                        .replaceFirst("```\\s*", "")
                        .trim();
                }
            }
        }

        // Strategy 2: Look for JSON object with evidenceScores field
        if (!jsonStr.startsWith("{")) {
            // First try to find the exact pattern we expect
            int jsonStart = jsonStr.indexOf("{\"evidenceScores\"");

            // If not found, try with spaces
            if (jsonStart == -1) {
                jsonStart = jsonStr.indexOf("{ \"evidenceScores\"");
            }

            // If still not found, just find the first opening brace
            if (jsonStart == -1) {
                jsonStart = jsonStr.indexOf("{");
            }

            if (jsonStart != -1) {
                // Find the matching closing brace
                int braceCount = 0;
                int jsonEnd = jsonStart;
                for (int i = jsonStart; i < jsonStr.length(); i++) {
                    char c = jsonStr.charAt(i);
                    if (c == '{') braceCount++;
                    else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            jsonEnd = i + 1;
                            break;
                        }
                    }
                }
                if (jsonEnd > jsonStart) {
                    jsonStr = jsonStr.substring(jsonStart, jsonEnd).trim();
                }
            }
        }

        log.info("Extracted JSON for parsing")
            .field("extractedLength", jsonStr.length())
            .field("preview", jsonStr.substring(0, Math.min(100, jsonStr.length())))
            .log();

        JsonNode json = objectMapper.readTree(jsonStr);
        JsonNode scores = json.get("evidenceScores");
        if (scores == null || !scores.isArray()) {
            throw new IllegalStateException("LLM response is missing the 'evidenceScores' array");
        }

        Map<String, Double> relevanceById = new LinkedHashMap<>();
        for (JsonNode item : scores) {
            JsonNode idNode = item.get("id");
            JsonNode relevanceNode = item.get("relevance");
            if (idNode != null && relevanceNode != null) {
                relevanceById.put(idNode.asText(), relevanceNode.asDouble());
            }
        }

        return relevanceById;
    }

    /**
     * Computes the final evidence ranking in code: {@code finalScore = 0.2 × strength + 0.8 ×
     * relevance}, sorted descending, capped at {@code cap} items.
     *
     * <p>Strength is the assertion-level {@code relevanceScore} carried on each input item;
     * relevance is the per-item RCA score the LLM returned. Running this arithmetic here — rather
     * than trusting the model to apply it — makes strength authoritative and the ordering
     * deterministic and unit-testable. Ids the LLM omitted (merged duplicates) and ids with no
     * matching input item are dropped.
     *
     * <p>Package-private and static so the formula can be exercised directly, without an LLM call.
     */
    static List<String> computeRanking(
            List<ValidationEvidence> evidenceList,
            Map<String, Double> relevanceById,
            int cap) {

        Map<String, ValidationEvidence> byId = evidenceList.stream()
            .collect(Collectors.toMap(ValidationEvidence::id, ev -> ev, (a, b) -> a, LinkedHashMap::new));

        return relevanceById.entrySet().stream()
            .filter(e -> byId.containsKey(e.getKey()))
            .sorted(Comparator.comparingDouble(
                    (Map.Entry<String, Double> e) ->
                        finalScore(byId.get(e.getKey()).relevanceScore(), e.getValue()))
                .reversed())
            .limit(cap)
            .map(Map.Entry::getKey)
            .toList();
    }

    /** Final ranking score — strength and relevance combined, strength weighted at 0.4. */
    static double finalScore(double strength, double relevance) {
        return STRENGTH_WEIGHT * strength + RELEVANCE_WEIGHT * relevance;
    }

    /**
     * Drops IDs the model invented or repeated, and caps the list at {@link #MAX_RANKED_ITEMS}.
     *
     * <p>The model is only allowed to select from the evidence it was given. If nothing valid
     * is left the response is unusable, so this throws and the caller falls back to
     * deterministic ranking instead of silently discarding all evidence.
     */
    private EvidenceRankingResponse sanitize(EvidenceRankingResponse response, List<ValidationEvidence> input) {
        var knownIds = input.stream().map(ValidationEvidence::id).collect(Collectors.toSet());
        List<String> validIds = response.rankedEvidenceIds().stream()
            .filter(knownIds::contains)
            .distinct()
            .limit(MAX_RANKED_ITEMS)
            .toList();

        if (validIds.size() != response.rankedEvidenceIds().size()) {
            log.warn("Discarded invalid or repeated evidence IDs from LLM ranking")
                .field("returned", response.rankedEvidenceIds().size())
                .field("valid", validIds.size())
                .log();
        }
        if (validIds.isEmpty() && !input.isEmpty()) {
            throw new IllegalStateException("LLM ranking contained no valid evidence IDs");
        }
        return new EvidenceRankingResponse(validIds, response.reasoning());
    }

    /**
     * Logs detailed ranking results showing input vs output and deduplication
     */
    private void logRankingResults(
            List<ValidationEvidence> inputEvidence,
            EvidenceRankingResponse response,
            String method) {

        log.info("📤 RANKING RESULTS")
            .field("method", method)
            .field("inputCount", inputEvidence.size())
            .field("outputCount", response.rankedEvidenceIds().size())
            .field("deduplicated", inputEvidence.size() - response.rankedEvidenceIds().size())
            .log();

        // Map IDs to evidence for easy lookup
        var evidenceMap = inputEvidence.stream()
            .collect(java.util.stream.Collectors.toMap(
                ValidationEvidence::id,
                ev -> ev
            ));

        // Log ranked output with statements
        log.info("📊 RANKED EVIDENCE (Top 20):")
            .log();

        int rank = 1;
        for (String id : response.rankedEvidenceIds()) {
            ValidationEvidence ev = evidenceMap.get(id);
            if (ev != null) {
                log.info(String.format("  [%d] %s", rank++, id))
                    .field("strength", String.format("%.2f", ev.relevanceScore()))
                    .field("statement", truncate(ev.statement(), 120))
                    .log();
            }
        }

        // Log what was deduplicated (input IDs not in output)
        var rankedIdSet = new java.util.HashSet<>(response.rankedEvidenceIds());
        var deduplicatedEvidence = inputEvidence.stream()
            .filter(ev -> !rankedIdSet.contains(ev.id()))
            .toList();

        if (!deduplicatedEvidence.isEmpty()) {
            log.info("🗑️ DEDUPLICATED/FILTERED EVIDENCE")
                .field("count", deduplicatedEvidence.size())
                .log();

            deduplicatedEvidence.forEach(ev -> {
                log.info("  ✗ Removed")
                    .field("id", ev.id())
                    .field("strength", String.format("%.2f", ev.relevanceScore()))
                    .field("statement", truncate(ev.statement(), 100))
                    .log();
            });
        }

        log.info("✅ Evidence ranking complete")
            .field("method", method)
            .field("finalCount", response.rankedEvidenceIds().size())
            .log();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
