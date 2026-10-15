package com.causa.core.services.validation.impl;

import java.util.Comparator;
import java.util.List;

import com.causa.common.constants.PromptConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.validation.Evidence;
import com.causa.core.domain.validation.EvidenceRankingRequest.RcaSummary;
import com.causa.core.domain.validation.EvidenceRankingResponse;
import com.causa.core.services.PromptTemplateLoader;
import com.causa.core.services.validation.EvidenceRanker;
import com.causa.llm.ChatModelFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * LLM-based evidence ranking and deduplication.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LlmEvidenceRanker implements EvidenceRanker {

    private static final CausaLogger log = CausaLogger.getLogger(LlmEvidenceRanker.class);

    private final ChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;
    private final PromptTemplateLoader promptTemplateLoader;

    @Inject
    public LlmEvidenceRanker(
            ChatModelFactory chatModelFactory,
            ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.objectMapper = objectMapper;
        this.promptTemplateLoader = new PromptTemplateLoader(PromptConstants.TEMPLATE_PATH_EVIDENCE_RANKING);
    }

    @Override
    public EvidenceRankingResponse rankAndDeduplicate(
            RcaSummary rca,
            List<Evidence> evidenceList,
            double validationScore,
            String validationStatus) {

        log.info("Evidence ranking placeholder - returning all evidence IDs")
            .field("evidenceCount", evidenceList.size())
            .log();

        // TODO: Implement LLM-based ranking using promptTemplateLoader
        // TODO: Add smart routing (≤10 items = skip LLM)
        // TODO: Add compact format generation
        // TODO: Add LLM call with prompt template
        // TODO: Add response parsing and validation

        // Placeholder: return all evidence IDs sorted by relevance score
        List<String> rankedIds = evidenceList.stream()
            .sorted(Comparator.comparingDouble(Evidence::relevanceScore).reversed())
            .map(Evidence::id)
            .toList();

        return new EvidenceRankingResponse(rankedIds, "Placeholder - not implemented");
    }
}
