package com.shopai.agent.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;

/**
 * Formats RAG retrieval results into a Chinese-language prompt context for the LLM.
 * Supports both legacy {@link EmbeddingMatch} and enriched parent-child results.
 */
public final class ResultFormatter {

    private ResultFormatter() {}

    /** Formats legacy EmbeddingMatch results (child-only, no parent enrichment). */
    static String format(List<EmbeddingMatch<TextSegment>> matches) {
        if (matches == null || matches.isEmpty()) {
            return noResults();
        }

        StringBuilder sb = new StringBuilder("【相关政策条款】\n\n");

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment seg = match.embedded();
            double score = match.score();
            String docName = seg.metadata().getString("file_name");
            if (docName == null) docName = seg.metadata().getString("absolute_directory_path");

            sb.append(String.format("%d. [来源: %s] (相关度: %.0f%%)\n",
                i + 1, docName, score * 100));
            sb.append("   ").append(seg.text()).append("\n\n");
        }

        sb.append("请根据以上政策条款回答用户的问题，并在回答中明确引用具体条款。");
        return sb.toString();
    }

    /** Formats enriched parent-child results with full parent context. */
    static String formatEnriched(List<PolicyRagService.EnrichedMatch> enriched) {
        if (enriched == null || enriched.isEmpty()) {
            return noResults();
        }

        StringBuilder sb = new StringBuilder("【相关政策条款】\n\n");

        for (int i = 0; i < enriched.size(); i++) {
            PolicyRagService.EnrichedMatch m = enriched.get(i);
            String source = m.sourceFile() != null ? m.sourceFile() : "未知来源";
            sb.append(String.format("%d. [来源: %s] (相关度: %.0f%%)\n",
                i + 1, source, m.score() * 100));
            sb.append("   ").append(m.contextText()).append("\n\n");
        }

        sb.append("请根据以上政策条款回答用户的问题，并在回答中明确引用具体条款。");
        return sb.toString();
    }

    private static String noResults() {
        return "【相关政策条款】\n"
            + "未找到相关的政策条款。请根据您已有的知识回答用户，"
            + "并建议用户联系人工客服获取最新政策信息。";
    }
}
