package com.shopai.agent.rag;

import dev.langchain4j.data.segment.TextSegment;

/**
 * A TextSegment with an associated relevance score.
 * Used across the enhanced RAG pipeline for consistent result passing
 * between vector search, keyword search, RRF fusion, and re-ranking stages.
 */
public record ScoredSegment(TextSegment segment, double score) {
}
