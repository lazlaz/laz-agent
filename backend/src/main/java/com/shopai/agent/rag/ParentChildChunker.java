package com.shopai.agent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Two-level document chunker: parent chunks (large, full context) and child chunks
 * (small, searchable). Only child chunks are embedded and stored in ChromaDB.
 * Parent chunks are stored in {@link ParentChunkStore} for context retrieval.
 *
 * <p>Design rationale: small chunks produce more precise embedding matches,
 * but returning only a small snippet loses context. Parent-child solves this:
 * search matches a child → resolve its parent → return the full parent paragraph
 * to the LLM.</p>
 */
public class ParentChildChunker {

    private static final Logger log = LoggerFactory.getLogger(ParentChildChunker.class);

    private final DocumentSplitter parentSplitter;
    private final DocumentSplitter childSplitter;
    private final int parentMaxSize;
    private final int childMaxSize;

    public ParentChildChunker(int parentMaxSize, int parentOverlap,
                              int childMaxSize, int childOverlap) {
        this.parentMaxSize = parentMaxSize;
        this.childMaxSize = childMaxSize;
        this.parentSplitter = new DocumentByParagraphSplitter(parentMaxSize, parentOverlap);
        this.childSplitter = new DocumentBySentenceSplitter(childMaxSize, childOverlap);
        log.info("ParentChildChunker: parent={}/{}, child={}/{}",
            parentMaxSize, parentOverlap, childMaxSize, childOverlap);
    }

    /** Result of chunking a single document. */
    public record ChunkResult(List<ParentChunk> parents, List<TextSegment> children) {}

    /** A parent chunk — stored in memory, not embedded. */
    public record ParentChunk(String parentId, String text, dev.langchain4j.data.document.Metadata metadata) {}

    /**
     * Chunks a document into parent-child structure.
     * Returns parents (for context storage) and children (for embedding/search).
     */
    public ChunkResult chunk(Document document) {
        List<TextSegment> parentSegments = parentSplitter.split(document);
        List<ParentChunk> parents = new ArrayList<>();
        List<TextSegment> allChildren = new ArrayList<>();

        for (TextSegment parentSeg : parentSegments) {
            String parentId = UUID.randomUUID().toString();
            parents.add(new ParentChunk(parentId, parentSeg.text(), parentSeg.metadata().copy()));

            // Split each parent into child chunks
            Document parentDoc = Document.from(parentSeg.text(), parentSeg.metadata().copy());
            List<TextSegment> childSegs = childSplitter.split(parentDoc);

            for (TextSegment child : childSegs) {
                child.metadata().put("parent_id", parentId);
                inheritMetadata(child.metadata(), parentSeg.metadata());
            }
            allChildren.addAll(childSegs);
        }

        log.debug("Chunked document: {} parent(s), {} child(ren) [parent={}c, child={}c]",
            parents.size(), allChildren.size(), parentMaxSize, childMaxSize);
        return new ChunkResult(parents, allChildren);
    }

    private void inheritMetadata(dev.langchain4j.data.document.Metadata child,
                                 dev.langchain4j.data.document.Metadata parent) {
        String fileName = parent.getString("file_name");
        if (fileName != null) {
            child.put("file_name", fileName);
        }
        String dirPath = parent.getString("absolute_directory_path");
        if (dirPath != null) {
            child.put("absolute_directory_path", dirPath);
        }
        // Propagate remaining metadata keys (e.g. file_type, ingestion_time)
        var parentMap = parent.toMap();
        for (var entry : parentMap.entrySet()) {
            String key = entry.getKey();
            if (!"parent_id".equals(key)
                && !"file_name".equals(key)
                && !"absolute_directory_path".equals(key)) {
                Object value = entry.getValue();
                if (value instanceof String s) {
                    child.put(key, s);
                } else if (value instanceof Integer i) {
                    child.put(key, i);
                } else if (value instanceof Long l) {
                    child.put(key, l);
                } else if (value instanceof Float f) {
                    child.put(key, f);
                } else if (value instanceof Double d) {
                    child.put(key, d);
                } else if (value instanceof java.util.UUID u) {
                    child.put(key, u);
                }
                // Skip unsupported types silently
            }
        }
    }
}
