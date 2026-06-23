package com.shopai.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PolicyRagService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRagService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ParentChunkStore parentStore;
    private final int topK;
    private final double minScore;

    public PolicyRagService(
        EmbeddingModel embeddingModel,
        EmbeddingStore<TextSegment> embeddingStore,
        ParentChunkStore parentStore,
        @Value("${shopai.rag.retrieval.top-k:3}") int topK,
        @Value("${shopai.rag.retrieval.min-score:0.5}") double minScore
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.parentStore = parentStore;
        this.topK = topK;
        this.minScore = minScore;
    }

    /**
     * Queries the knowledge base and returns formatted policy text for the LLM.
     * Child chunks are matched via vector similarity, then enriched with their
     * parent chunk context for complete policy clause text.
     */
    public String query(String question) {
        long start = System.currentTimeMillis();

        // 1. Embed query
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        // 2. Search ChromaDB for matching child chunks
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(minScore)
            .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        // 3. Enrich with parent context (resolve parent_id → full parent text)
        List<EnrichedMatch> enriched = matches.stream()
            .map(this::enrichWithParent)
            .filter(Objects::nonNull)
            .toList();

        long elapsed = System.currentTimeMillis() - start;
        log.info("RAG query: '{}' → {} matches, {} enriched in {}ms",
            question, matches.size(), enriched.size(), elapsed);

        // 4. Format for LLM prompt
        return ResultFormatter.formatEnriched(enriched);
    }

    /**
     * Resolves a child chunk match to its parent chunk for full context.
     * Falls back to the child text if no parent_id is found (legacy data).
     */
    private EnrichedMatch enrichWithParent(EmbeddingMatch<TextSegment> match) {
        TextSegment child = match.embedded();
        String parentId = child.metadata().getString("parent_id");
        String sourceFile = child.metadata().getString("file_name");

        if (parentId == null) {
            // Legacy data: no parent-child structure, use child text directly
            return new EnrichedMatch(child.text(), match.score(), sourceFile);
        }

        ParentChunkStore.ParentChunkRecord parent = parentStore.get(parentId);
        String contextText = parent != null ? parent.text() : child.text();
        return new EnrichedMatch(contextText, match.score(), sourceFile);
    }

    /** A search result enriched with full parent context. */
    record EnrichedMatch(String contextText, double score, String sourceFile) {}
}
