package com.shopai.agent.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test configuration that replaces external dependencies with in-memory fakes.
 * <p>
 * Activated by {@code @Import(TestConfig.class)} in integration test classes.
 * <ul>
 *   <li>{@link EmbeddingModel} → {@link StubEmbeddingModel} (zero vectors, no Python sidecar)</li>
 *   <li>{@link EmbeddingStore} → {@link ProgrammableEmbeddingStore} (in-memory, pre-configured results)</li>
 * </ul>
 * All other beans are inherited from {@link AgentConfig}.
 */
@TestConfiguration
public class TestConfig {

    /**
     * Stub embedding model that returns zero-vector embeddings.
     * <p>
     * Real embeddings are not needed in tests because {@link ProgrammableEmbeddingStore}
     * returns pre-configured results regardless of the query vector. The embed() call
     * in PolicyRagService is satisfied without needing a running Python sidecar.
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return new StubEmbeddingModel();
    }

    /**
     * Programmable in-memory embedding store for RAG integration tests.
     * <p>
     * Register expected {@link EmbeddingMatch} results per query text via
     * {@link #programResults(Map)}, or globally via {@link #setGlobalResults(List)}.
     * The store ignores the query vector and returns whatever is programmed.
     */
    @Bean
    @Primary
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new ProgrammableEmbeddingStore();
    }

    // ── Fake implementations ────────────────────────────────────────────

    /** Returns a 384-dim zero vector for any input. */
    public static class StubEmbeddingModel implements EmbeddingModel {

        private static final int DIM = 384;

        @Override
        public Response<Embedding> embed(String text) {
            return Response.from(Embedding.from(new float[DIM]));
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = segments.stream()
                .map(s -> Embedding.from(new float[DIM]))
                .toList();
            return Response.from(embeddings);
        }
    }

    /**
     * In-memory {@link EmbeddingStore} that returns pre-configured results.
     * <p>
     * Usage in tests:
     * <pre>{@code
     *   ProgrammableEmbeddingStore store = (ProgrammableEmbeddingStore) embeddingStore;
     *   store.setGlobalResults(List.of(
     *       EmbeddingMatch.from(0.92, "id-1", TextSegment.from("text", metadata))
     *   ));
     * }</pre>
     */
    public static class ProgrammableEmbeddingStore implements EmbeddingStore<TextSegment> {

        private final Map<String, List<EmbeddingMatch<TextSegment>>> programMap = new ConcurrentHashMap<>();
        private volatile List<EmbeddingMatch<TextSegment>> globalResults = Collections.emptyList();

        // ── Abstract methods (must implement) ─────────────────────

        @Override
        public String add(Embedding embedding) {
            return "fake-id-" + System.nanoTime();
        }

        @Override
        public void add(String id, Embedding embedding) {
            // no-op for tests
        }

        @Override
        public String add(Embedding embedding, TextSegment textSegment) {
            return "fake-id-" + System.nanoTime();
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return embeddings.stream().map(e -> "fake-id-" + System.nanoTime()).toList();
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
            return textSegments.stream().map(t -> "fake-id-" + System.nanoTime()).toList();
        }

        // ── Default method overrides ──────────────────────────────

        @Override
        public void removeAll() {
            programMap.clear();
            globalResults = Collections.emptyList();
        }

        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            List<EmbeddingMatch<TextSegment>> matches = globalResults;
            if (matches == null || matches.isEmpty()) {
                matches = Collections.emptyList();
            }

            // Apply maxResults and minScore from request
            List<EmbeddingMatch<TextSegment>> filtered = matches.stream()
                .filter(m -> m.score() >= request.minScore())
                .limit(request.maxResults())
                .toList();

            return new EmbeddingSearchResult<>(filtered);
        }

        /**
         * Set results that will be returned for ALL subsequent search() calls.
         */
        public void setGlobalResults(List<EmbeddingMatch<TextSegment>> results) {
            this.globalResults = new ArrayList<>(results);
        }

        /**
         * Clear all programmed results.
         */
        public void reset() {
            programMap.clear();
            globalResults = Collections.emptyList();
        }
    }
}
