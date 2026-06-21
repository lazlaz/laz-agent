package com.shopai.agent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class DocumentIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Path policiesPath;

    public DocumentIndexService(
        EmbeddingModel embeddingModel,
        EmbeddingStore<TextSegment> embeddingStore,
        @Value("${shopai.rag.policies-dir:policies/}") String policiesDir
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;

        // 解析相对于 resources 的路径
        String resourcePath = this.getClass().getClassLoader().getResource("").getPath();
        this.policiesPath = Paths.get(resourcePath, policiesDir).normalize();
    }

    public int buildIndex() {
        log.info("Building RAG index from: {}", policiesPath);

        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
            policiesPath, new TextDocumentParser()
        );

        if (documents.isEmpty()) {
            log.warn("No policy documents found in {}", policiesPath);
            return 0;
        }

        log.info("Loaded {} documents, clearing existing index...", documents.size());
        embeddingStore.removeAll();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
            .documentSplitter(new DocumentByParagraphSplitter(1000, 0))
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();

        ingestor.ingest(documents);

        log.info("Index rebuild complete: {} documents ingested", documents.size());
        return documents.size();
    }
}
