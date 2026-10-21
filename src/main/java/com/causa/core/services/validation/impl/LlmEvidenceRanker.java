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

            return new EvidenceRankingResponse(rankedIds, "Sorted by relevanceScore (no LLM ranking)");
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
            return parseResponse(responseText);

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

            return new EvidenceRankingResponse(rankedIds, "Fallback ranking (LLM failed)");
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

        // Ultra-compact evidence format: "ID: statement"
        String evidenceCompact = evidenceList.stream()
            .map(ev -> String.format("%s: %s", ev.id(), ev.statement()))
            .collect(Collectors.joining("\n"));

        return template.userPrompt()
            .replace("{{rca_summary}}", truncate(rca.rootCause(), 150))
            .replace("{{validation_status}}", validationStatus)
            .replace("{{validation_score}}", String.format("%.0f", validationScore * 100))
            .replace("{{evidence_compact}}", evidenceCompact);
    }

    private EvidenceRankingResponse parseResponse(String response) throws JsonProcessingException {
        // Extract JSON from response (handles markdown code blocks)
        String jsonStr = response.trim();
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.replaceFirst("```json\\s*", "")
                .replaceFirst("```\\s*$", "")
                .trim();
        }

        JsonNode json = objectMapper.readTree(jsonStr);
        List<String> rankedIds = objectMapper.convertValue(
            json.get("rankedEvidenceIds"),
            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );

        String reasoning = json.has("reasoning") ? json.get("reasoning").asText() : "LLM ranking";

        return new EvidenceRankingResponse(rankedIds, reasoning);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
