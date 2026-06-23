package com.shopai.agent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class DocumentIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Path policiesPath;
    private final EmbeddingStoreIngestor ingestor;

    public DocumentIndexService(
        EmbeddingModel embeddingModel,
        EmbeddingStore<TextSegment> embeddingStore,
        @Value("${shopai.rag.policies-dir:policies/}") String policiesDir
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;

        // 解析相对于 resources 的路径
        String resourcePath = this.getClass().getClassLoader().getResource("").getPath();
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        this.policiesPath = Paths.get(resourcePath, policiesDir).normalize();

        // 共享的 Ingestor 实例（splitter + embeddingModel + store 都是线程安全的）
        this.ingestor = EmbeddingStoreIngestor.builder()
            .documentSplitter(new DocumentByParagraphSplitter(1000, 0))
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();
    }

    // ── 启动时全量同步 ────────────────────────────────────────────

    @PostConstruct
    public void init() {
        try {
            int count = buildIndex();
            log.info("Startup index build: {} documents indexed", count);
        } catch (Exception e) {
            log.warn("Startup index build failed (Chroma/sidecar 可能未启动): {}. "
                     + "可稍后通过 POST /api/knowledge/rebuild 手动重建索引。",
                     e.getMessage());
        }
    }

    // ── 全量重建（removeAll + 重新摄入所有文件） ──────────────────

    public int buildIndex() {
        log.info("Building full RAG index from: {}", policiesPath);

        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
            policiesPath, new TextDocumentParser()
        );

        if (documents.isEmpty()) {
            log.warn("No policy documents found in {}", policiesPath);
            return 0;
        }

        log.info("Loaded {} documents, clearing existing index...", documents.size());
        embeddingStore.removeAll();

        ingestor.ingest(documents);

        log.info("Full index rebuild complete: {} documents ingested", documents.size());
        return documents.size();
    }

    // ── 增量添加：只索引单个文件，不清除已有数据 ──────────────────

    public int indexDocument(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("Document not found for indexing: {}", filePath);
            return -1;
        }

        Document doc = FileSystemDocumentLoader.loadDocument(filePath, new TextDocumentParser());
        String filename = filePath.getFileName().toString();
        log.info("Incremental indexing: {} ...", filename);

        // ingest(Document) 不会调用 removeAll，只追加到 store
        ingestor.ingest(doc);

        log.info("Document indexed incrementally: {}", filename);
        return 1;
    }

    // ── 增量移除：按文件名元数据删除对应的 embeddings ────────────

    public void removeDocument(String filename) {
        Filter filter = MetadataFilterBuilder.metadataKey("file_name").isEqualTo(filename);
        embeddingStore.removeAll(filter);
        log.info("Document embeddings removed for: {}", filename);
    }
}
