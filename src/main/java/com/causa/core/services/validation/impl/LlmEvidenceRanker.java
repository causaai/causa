package com.causa.core.services.validation.impl;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.causa.common.constants.PromptConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.validation.ValidationEvidence;
import com.causa.core.domain.validation.EvidenceRankingRequest.RcaSummary;
import com.causa.core.domain.validation.EvidenceRankingResponse;
import com.causa.core.services.PromptTemplateLoader;
import com.causa.core.services.validation.EvidenceRanker;
import com.causa.llm.ChatModelFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Evidence ranking and deduplication with smart routing optimization.
 *
 * <p>Ranks PATH A evidence (from assertion validation). PATH B evidence (from rule engine)
 * bypasses ranking and goes directly to EvidenceItem storage.
 *
 * <p>For ≤3 items: sorts by existing relevanceScore (no additional LLM call).
 * For >3 items: uses LLM for intelligent ranking and deduplication.
 *
 * <p>Note: relevanceScore is set during assertion validation by LLM or rule-based matcher.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LlmEvidenceRanker implements EvidenceRanker {

    private static final CausaLogger log = CausaLogger.getLogger(LlmEvidenceRanker.class);
    private static final int LLM_THRESHOLD = 3;
    private static final int MAX_RANKED_ITEMS = 20;

    private final ChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LlmConfigCache llmConfigCache;

    @Inject
    public LlmEvidenceRanker(
            ChatModelFactory chatModelFactory,
            ObjectMapper objectMapper,
            LlmConfigCache llmConfigCache) {
        this.chatModelFactory = chatModelFactory;
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

        // Smart routing: ≤3 items = skip LLM ranking, sort by existing relevanceScore
        // (relevanceScore was already calculated during assertion validation)
        if (statementEvidence.size() <= LLM_THRESHOLD) {
            log.info("Skipping LLM ranking (≤" + LLM_THRESHOLD + " items), sorting by existing relevanceScore")
                .log();

            List<String> rankedIds = statementEvidence.stream()
                .sorted(Comparator.comparingDouble(ValidationEvidence::relevanceScore).reversed())
                .limit(MAX_RANKED_ITEMS)
                .map(ValidationEvidence::id)
                .toList();

            EvidenceRankingResponse response = new EvidenceRankingResponse(rankedIds, "Sorted by relevanceScore (no LLM ranking)");
            logRankingResults(statementEvidence, response, "Deterministic (≤3 items)");
            return response;
        }

        // >3 items: use LLM for intelligent ranking and deduplication
        return rankWithLlm(rca, statementEvidence, validationScore, validationStatus);
    }

    private EvidenceRankingResponse rankWithLlm(
            RcaSummary rca,
            List<ValidationEvidence> evidenceList,
            double validationScore,
            String validationStatus) {

        try {
            PromptTemplateLoader.PromptTemplate template = promptTemplateLoader.loadTemplate(resolveProvider(), "");
            String compactPrompt = buildCompactPrompt(template, rca, evidenceList, validationScore, validationStatus);

            log.info("Calling LLM for evidence ranking")
                .field("evidenceCount", evidenceList.size())
                .log();

            // Build chat request with system and user messages
            List<ChatMessage> messages = List.of(
                SystemMessage.from(template.systemPrompt()),
                UserMessage.from(compactPrompt)
            );

            ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

            ChatModel model = chatModelFactory.chatModel();
            ChatResponse response = model.chat(chatRequest);

            String responseText = response.aiMessage().text();
            log.info("LLM response received")
                .field("responseLength", responseText.length())
                .field("responsePreview", responseText.substring(0, Math.min(200, responseText.length())))
                .log();

            EvidenceRankingResponse rankingResponse = parseResponse(responseText);

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
        String providerName = llmConfigCache.getActive()
            .map(config -> config.getProvider().name())
            .orElse("anthropic");
        return providerName.toLowerCase().replace("_", "-");
    }

    private String buildCompactPrompt(
            PromptTemplateLoader.PromptTemplate template,
            RcaSummary rca,
            List<ValidationEvidence> evidenceList,
            double validationScore,
            String validationStatus) {

        // Evidence format: "ID: statement [strength: score]"
        // Strength = assertion-level relevanceScore (used in formula: 0.4 × strength + 0.6 × relevance)
        String evidenceCompact = evidenceList.stream()
            .map(ev -> String.format("%s: %s [strength: %.2f]", ev.id(), ev.statement(), ev.relevanceScore()))
            .collect(Collectors.joining("\n"));

        return template.userPrompt()
            .replace("{{rca_summary}}", truncate(rca.rootCause(), 150))
            .replace("{{validation_status}}", validationStatus)
            .replace("{{validation_score}}", String.format("%.0f", validationScore * 100))
            .replace("{{evidence_compact}}", evidenceCompact);
    }

    private EvidenceRankingResponse parseResponse(String response) throws JsonProcessingException {
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

        // Strategy 2: Look for JSON object with rankedEvidenceIds field
        if (!jsonStr.startsWith("{")) {
            // First try to find the exact pattern we expect
            int jsonStart = jsonStr.indexOf("{\"rankedEvidenceIds\"");

            // If not found, try with spaces
            if (jsonStart == -1) {
                jsonStart = jsonStr.indexOf("{ \"rankedEvidenceIds\"");
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
        List<String> rankedIds = objectMapper.convertValue(
            json.get("rankedEvidenceIds"),
            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        String reasoning = json.has("reasoning") ? json.get("reasoning").asText() : "LLM ranking";

        return new EvidenceRankingResponse(rankedIds, reasoning);
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
