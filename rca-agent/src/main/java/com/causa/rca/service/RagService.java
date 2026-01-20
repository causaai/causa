package com.causa.rca.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service for managing RAG (Retrieval-Augmented Generation) functionality.
 * <p>
 * This service handles the ingestion of knowledge base documents (runbooks, troubleshooting
 * guides, best practices) into an embedding store. The embedded documents can then be
 * retrieved to provide context to AI models, enhancing their responses with domain-specific
 * knowledge.
 * </p>
 * <p>
 * The service uses LangChain4j to:
 * <ul>
 *   <li>Load documents from the file system</li>
 *   <li>Generate embeddings using an embedding model</li>
 *   <li>Store embeddings in an in-memory vector store</li>
 *   <li>Enable semantic search for relevant context retrieval</li>
 * </ul>
 * </p>
 *
 * @see LifecycleService
 * @see RagConfig
 */
@ApplicationScoped
public class RagService {

    private static final Logger LOG = Logger.getLogger(RagService.class);

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    /**
     * Produces the EmbeddingStore bean for dependency injection.
     * <p>
     * Creates an in-memory embedding store that holds vector representations of
     * ingested documents. This store enables fast semantic similarity searches
     * to retrieve relevant context for AI queries.
     * </p>
     *
     * @return a new InMemoryEmbeddingStore instance
     */
    @Produces
    @ApplicationScoped
    public EmbeddingStore<TextSegment> produceEmbeddingStore() {
        LOG.info("Creating InMemoryEmbeddingStore bean...");
        return new InMemoryEmbeddingStore<>();
    }

    @ConfigProperty(name = "rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    @ConfigProperty(name = "rag.knowledge-base.path", defaultValue = "knowledge_base")
    String knowledgeBasePath;

    /**
     * Ingests documents from the knowledge base into the embedding store.
     * <p>
     * Loads all text documents from the configured knowledge base path, generates
     * embeddings for them using the embedding model, and stores them in the
     * embedding store for later retrieval. This method is typically called during
     * application startup.
     * </p>
     * <p>
     * The knowledge base should contain domain-specific documents such as:
     * <ul>
     *   <li>Runbooks for common issues (OOM, CPU throttling, etc.)</li>
     *   <li>Troubleshooting guides</li>
     *   <li>Best practices and solutions</li>
     * </ul>
     * </p>
     */
    public void ingestDocuments() {
        try {
            LOG.info("Starting document ingestion from: " + knowledgeBasePath);
            Path path = Paths.get(knowledgeBasePath);
            if (!path.toFile().exists()) {
                LOG.warn("Knowledge base path does not exist: " + knowledgeBasePath);
                return;
            }

            List<Document> documents = FileSystemDocumentLoader.loadDocuments(path, new TextDocumentParser());

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .build();

            ingestor.ingest(documents);
            LOG.info("Successfully ingested " + documents.size() + " documents into RAG store.");
        } catch (Exception e) {
            LOG.error("Failed to ingest documents", e);
        }
    }
}
