package com.shopai.agent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for parent chunks. During retrieval, child chunks are searched
 * via embeddings in ChromaDB, then their parent chunks are fetched from here for
 * full-context responses to the LLM.
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap}. In a clustered deployment this
 * would be replaced with a shared cache (Redis) or embedded in Chroma metadata.</p>
 */
public class ParentChunkStore {

    private static final Logger log = LoggerFactory.getLogger(ParentChunkStore.class);

    private final Map<String, ParentChunkRecord> store = new ConcurrentHashMap<>();

    public record ParentChunkRecord(String parentId, String text, String sourceFileName) {}

    /**
     * Stores a batch of parent chunks. Called after chunking during ingestion.
     */
    public void store(java.util.List<ParentChildChunker.ParentChunk> parents) {
        for (var p : parents) {
            String source = p.metadata() != null ? p.metadata().getString("file_name") : null;
            store.put(p.parentId(), new ParentChunkRecord(p.parentId(), p.text(), source));
        }
        log.debug("Stored {} parent chunks (total: {})", parents.size(), store.size());
    }

    /**
     * Retrieves a parent chunk by its ID.
     * Returns null if not found (e.g., evicted or never stored).
     */
    public ParentChunkRecord get(String parentId) {
        return store.get(parentId);
    }

    /**
     * Removes all parent chunks associated with a given source file.
     * Called when a document is deleted from the knowledge base.
     */
    public void removeBySource(String fileName) {
        int before = store.size();
        store.entrySet().removeIf(e -> fileName.equals(e.getValue().sourceFileName()));
        int removed = before - store.size();
        if (removed > 0) {
            log.info("Removed {} parent chunks for source: {}", removed, fileName);
        }
    }

    /** Clears all parent chunks. Called during full index rebuild. */
    public void clear() {
        int size = store.size();
        store.clear();
        log.info("Cleared all {} parent chunks", size);
    }

    /** Returns the number of stored parent chunks. */
    public int size() {
        return store.size();
    }
}
