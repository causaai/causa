package com.causa.rca.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Configuration class for RAG (Retrieval-Augmented Generation) feature.
 * <p>
 * This class manages configuration for the RAG system, which enhances AI responses
 * by retrieving relevant information from a knowledge base of documents (runbooks,
 * troubleshooting guides, etc.) before generating responses.
 * </p>
 * <p>
 * <b>Note:</b> The RetrievalAugmentor producer method is currently commented out.
 * The RAG functionality is implemented directly in {@link RagService} using
 * in-memory embedding store and document ingestion.
 * </p>
 *
 * @see RagService
 */
@ApplicationScoped
public class RagConfig {

    // Commented out for future use - currently RAG is configured in RagService
    // @Inject
    // EmbeddingStore<TextSegment> embeddingStore;
    //
    // @Inject
    // EmbeddingModel embeddingModel;

    /**
     * Flag indicating whether RAG feature is enabled.
     * Defaults to true if not specified in configuration.
     */
    @ConfigProperty(name = "rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    // Commented out - RetrievalAugmentor configuration for future enhancement
    // This would enable automatic retrieval augmentation for AI service calls
    // @Produces
    // public RetrievalAugmentor retrievalAugmentor() {
    // // if (!ragEnabled) {
    // return null;
    // // }
    // //
    // // ContentRetriever contentRetriever =
    // EmbeddingStoreContentRetriever.builder()
    // // .embeddingStore(embeddingStore)
    // // .embeddingModel(embeddingModel)
    // // .maxResults(3)
    // // .minScore(0.6)
    // // .build();
    // //
    // // return DefaultRetrievalAugmentor.builder()
    // // .contentRetriever(contentRetriever)
    // // .build();
    // }
}
