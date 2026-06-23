package com.shopai.agent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DocumentIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

    // Current supported file extensions (configurable via application.yml)
    private static final Set<String> ALLOWED_EXTENSIONS =
        Set.of(".md", ".txt", ".pdf", ".docx", ".doc", ".pptx", ".xlsx");

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Path policiesPath;
    private final ParentChildChunker chunker;
    private final ParentChunkStore parentStore;
    private final ImageDescriptionService imageDescriber;
    private final TableExtractor tableExtractor;

    public DocumentIndexService(
        EmbeddingModel embeddingModel,
        EmbeddingStore<TextSegment> embeddingStore,
        @Value("${shopai.rag.policies-dir:policies/}") String policiesDir,
        ParentChildChunker chunker,
        ParentChunkStore parentStore,
        ImageDescriptionService imageDescriber,
        TableExtractor tableExtractor
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chunker = chunker;
        this.parentStore = parentStore;
        this.imageDescriber = imageDescriber;
        this.tableExtractor = tableExtractor;

        // Resolve policies directory relative to resources
        String resourcePath = this.getClass().getClassLoader().getResource("").getPath();
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        this.policiesPath = Paths.get(resourcePath, policiesDir).normalize();
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

        // Load all supported documents from policies directory
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
            policiesPath, new ApacheTikaDocumentParser()
        );

        if (documents.isEmpty()) {
            log.warn("No policy documents found in {}", policiesPath);
            return 0;
        }

        log.info("Loaded {} documents, clearing existing index...", documents.size());
        embeddingStore.removeAll();
        parentStore.clear();

        int totalDocs = 0;
        int totalChildren = 0;
        int totalParents = 0;

        for (Document doc : documents) {
            try {
                IndexResult result = indexDocumentInternal(doc);
                totalDocs++;
                totalChildren += result.childCount();
                totalParents += result.parentCount();
            } catch (Exception e) {
                log.error("Failed to index document during rebuild, continuing: {}", e.getMessage());
            }
        }

        log.info("Full index rebuild complete: {} documents, {} parent chunks, {} child chunks indexed",
            totalDocs, totalParents, totalChildren);
        return totalDocs;
    }

    // ── 增量添加：只索引单个文件，不清除已有数据 ──────────────────

    public int indexDocument(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("Document not found for indexing: {}", filePath);
            return -1;
        }

        String filename = filePath.getFileName().toString();
        log.info("Incremental indexing: {} ...", filename);

        // Load single document with Tika parser
        Document doc = FileSystemDocumentLoader.loadDocument(filePath, new ApacheTikaDocumentParser());

        // Enrich with per-file metadata not provided by the loader
        doc.metadata().put("file_name", filename);
        doc.metadata().put("ingestion_time", Instant.now().toString());
        doc.metadata().put("file_type", extension(filename));

        IndexResult result = indexDocumentInternal(doc);

        log.info("Document indexed incrementally: {} → {} parent(s), {} child(ren)",
            filename, result.parentCount(), result.childCount());
        return 1;
    }

    // ── Internal: core ingestion pipeline ──────────────────────────

    private IndexResult indexDocumentInternal(Document doc) {
        String fileName = doc.metadata().getString("file_name");

        // 1. Build effective text: original + optional image descriptions (PDF only)
        String text = doc.text();
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
            String enriched = enrichWithImageDescriptions(fileName);
            if (enriched != null) {
                text = enriched;
            }
        }

        // 2. Post-process tables (Phase 1: pass-through; future: Markdown formatting)
        text = tableExtractor.enhanceTableText(text);
        Document processed = Document.from(text, doc.metadata());

        // 3. Parent-child chunking
        ParentChildChunker.ChunkResult chunkResult = chunker.chunk(processed);
        if (chunkResult.children().isEmpty()) {
            log.debug("No child chunks produced for document, skipping embed");
            return new IndexResult(0, 0);
        }

        // 4. Store parent chunks for later context retrieval
        parentStore.store(chunkResult.parents());

        // 5. Embed child chunks
        List<TextSegment> children = chunkResult.children();
        Response<List<Embedding>> embedResponse = embeddingModel.embedAll(children);

        // 6. Store children + embeddings in ChromaDB
        embeddingStore.addAll(embedResponse.content(), children);

        return new IndexResult(chunkResult.parents().size(), children.size());
    }

    /**
     * Extracts and describes images from a PDF, returning the original text
     * with descriptions appended. Returns null if no descriptions were generated.
     */
    private String enrichWithImageDescriptions(String fileName) {
        try {
            Path filePath = policiesPath.resolve(fileName);
            if (!Files.exists(filePath)) {
                log.debug("Cannot find file for image extraction: {}", filePath);
                return null;
            }

            List<ImageDescriptionService.ImageDescription> descriptions =
                imageDescriber.describe(filePath);

            if (!descriptions.isEmpty()) {
                StringBuilder enriched = new StringBuilder();
                enriched.append("\n\n【图片内容描述】\n");
                for (var desc : descriptions) {
                    enriched.append(desc.toIndexableText()).append("\n");
                }
                log.debug("Generated {} image descriptions for {}", descriptions.size(), fileName);
                return enriched.toString(); // caller prepends original text
            }
        } catch (Exception e) {
            log.warn("Image description failed for {}, continuing without: {}", fileName, e.getMessage());
        }
        return null;
    }

    // ── 增量移除：按文件名元数据删除对应的 embeddings ────────────

    public void removeDocument(String filename) {
        Filter filter = MetadataFilterBuilder.metadataKey("file_name").isEqualTo(filename);
        embeddingStore.removeAll(filter);
        parentStore.removeBySource(filename);
        log.info("Document removed from index: {}", filename);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    public static boolean isAllowedExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = filename.substring(dot).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    private record IndexResult(int parentCount, int childCount) {}
}
