package com.shopai.agent.integration.rag;

import com.shopai.agent.config.TestConfig;
import com.shopai.agent.config.TestConfig.ProgrammableEmbeddingStore;
import com.shopai.agent.rag.ParentChildChunker;
import com.shopai.agent.rag.ParentChunkStore;
import com.shopai.agent.rag.PolicyRagService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the RAG retrieval pipeline.
 * <p>
 * {@link EmbeddingModel} and {@link EmbeddingStore} are replaced with fake
 * implementations from {@link TestConfig}. The real {@link PolicyRagService}
 * and {@link ParentChunkStore} are tested.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("integration")
@DisplayName("RAG Retrieval Integration Tests")
class RagRetrievalTest {

    private static final Embedding ZERO_EMBEDDING = Embedding.from(new float[384]);

    @Autowired
    private PolicyRagService ragService;

    @Autowired
    private ParentChunkStore parentStore;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    /** Helper to create an EmbeddingMatch with a zero embedding vector. */
    private static EmbeddingMatch<TextSegment> match(double score, String id, TextSegment segment) {
        return new EmbeddingMatch<>(score, id, ZERO_EMBEDDING, segment);
    }

    @BeforeEach
    void setUp() {
        parentStore.clear();
        ((ProgrammableEmbeddingStore) embeddingStore).reset();
    }

    @Test
    @DisplayName("Should return parent context enriched result for child chunk match")
    void shouldEnrichChildMatchWithParentContext() {
        // Given: a parent chunk with full policy text
        String parentId = "parent-001";
        String parentText = "退货政策：用户可在购买后7天内无理由退货，商品需保持完好。超过7天但在15天内需支付15%折旧费。超过15天不支持退货。";

        Metadata parentMeta = Metadata.from("file_name", "return-policy.md");
        parentStore.store(List.of(
            new ParentChildChunker.ParentChunk(parentId, parentText, parentMeta)));

        // And: a child chunk that matches the query
        TextSegment childSegment = TextSegment.from(
            "7天内无理由退货",
            Metadata.from("parent_id", parentId).put("file_name", "return-policy.md"));

        ProgrammableEmbeddingStore store = (ProgrammableEmbeddingStore) embeddingStore;
        store.setGlobalResults(List.of(
            match(0.92, "child-001", childSegment)));

        // When
        String result = ragService.query("如何退货？");

        // Then: result contains the parent context (enriched), not just the child snippet
        assertThat(result)
            .contains("7天内无理由退货")       // Child text present
            .contains("退货政策")               // Parent context enriched
            .contains("相关政策条款")           // Formatter header present
            .contains("return-policy.md");      // Source file cited
    }

    @Test
    @DisplayName("Should fall back to child text when no parent_id exists")
    void shouldFallbackToChildTextWithoutParent() {
        // Given: a legacy child chunk without parent_id
        TextSegment legacySegment = TextSegment.from(
            "保修期为自购买之日起一年",
            Metadata.from("file_name", "warranty-policy.md"));
        // No parent_id metadata

        ProgrammableEmbeddingStore store = (ProgrammableEmbeddingStore) embeddingStore;
        store.setGlobalResults(List.of(
            match(0.88, "legacy-001", legacySegment)));

        // When
        String result = ragService.query("保修多久？");

        // Then: returns the child text directly
        assertThat(result)
            .contains("保修期为自购买之日起一年")
            .contains("warranty-policy.md");
    }

    @Test
    @DisplayName("Should return 'not found' message when no matches")
    void shouldReturnNoResultsWhenNoMatches() {
        // Given: no results programmed
        ProgrammableEmbeddingStore store = (ProgrammableEmbeddingStore) embeddingStore;
        store.setGlobalResults(List.of());

        // When
        String result = ragService.query("今天天气怎么样？");

        // Then
        assertThat(result)
            .contains("未找到相关的政策条款");
    }

    @Test
    @DisplayName("Should respect maxResults limit from config")
    void shouldRespectMaxResultsLimit() {
        // Given: 5 results in store, but config says top-k=3
        Metadata parentMeta = Metadata.from("file_name", "return-policy.md");
        parentStore.store(List.of(
            new ParentChildChunker.ParentChunk("p-1", "退换货政策内容A", parentMeta)));

        List<EmbeddingMatch<TextSegment>> manyMatches = List.of(
            match(0.95, "c-1", TextSegment.from("A",
                Metadata.from("parent_id", "p-1").put("file_name", "f1.md"))),
            match(0.90, "c-2", TextSegment.from("B",
                Metadata.from("parent_id", "p-1").put("file_name", "f2.md"))),
            match(0.85, "c-3", TextSegment.from("C",
                Metadata.from("parent_id", "p-1").put("file_name", "f3.md"))),
            match(0.80, "c-4", TextSegment.from("D",
                Metadata.from("parent_id", "p-1").put("file_name", "f4.md"))),
            match(0.75, "c-5", TextSegment.from("E",
                Metadata.from("parent_id", "p-1").put("file_name", "f5.md")))
        );

        ProgrammableEmbeddingStore store = (ProgrammableEmbeddingStore) embeddingStore;
        store.setGlobalResults(manyMatches);

        // When
        String result = ragService.query("政策查询");

        // Then: only top-3 (config value) results should appear
        // Each result has an "### 来源" section — count them
        long sourceCount = result.lines()
            .filter(line -> line.contains("来源"))
            .count();
        assertThat(sourceCount).isLessThanOrEqualTo(3);
    }

    // ── Hit Rate parameterized test ─────────────────────────────────────

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        how to return a product?     | return-policy.md
        warranty duration?           | warranty-policy.md
        shipping options?            | shipping-policy.md
        """)
    @DisplayName("RAG hit rate: query should retrieve expected document")
    void hitRateTest(String question, String expectedFile) {
        // Seed parent chunks for all known policies
        seedPolicyDocuments();

        // Program the store to return chunks from the expected document
        ProgrammableEmbeddingStore store = (ProgrammableEmbeddingStore) embeddingStore;
        store.setGlobalResults(List.of(
            match(0.5, "any", TextSegment.from("Policy text here",
                Metadata.from("parent_id", "p-return").put("file_name", expectedFile)))
        ));

        String result = ragService.query(question);

        assertThat(result).contains(expectedFile);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void seedPolicyDocuments() {
        parentStore.store(List.of(
            new ParentChildChunker.ParentChunk("p-return", "退货政策：7天无理由退货，15天折旧退货。",
                Metadata.from("file_name", "return-policy.md")),
            new ParentChildChunker.ParentChunk("p-warranty", "保修政策：一年保修，可延长至三年。",
                Metadata.from("file_name", "warranty-policy.md")),
            new ParentChildChunker.ParentChunk("p-shipping", "物流政策：默认顺丰，满99包邮。",
                Metadata.from("file_name", "shipping-policy.md"))
        ));
    }
}
