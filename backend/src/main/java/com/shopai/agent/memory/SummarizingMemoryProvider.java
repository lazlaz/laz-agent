package com.shopai.agent.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ChatMemoryProvider} that automatically summarizes older messages
 * when the conversation exceeds a configurable threshold.
 * <p>
 * Strategy:
 * <ol>
 *   <li>Read all messages from the {@link ChatMemoryStore}.</li>
 *   <li>If total &le; {@code maxBeforeSummary}, return them as-is.</li>
 *   <li>If total &gt; {@code maxBeforeSummary}, split the list:
 *       oldest (to summarize) + recent (to keep intact).</li>
 *   <li>Call the LLM to generate a concise summary of the oldest messages.</li>
 *   <li>Return a {@link ChatMemory} that injects the summary as a
 *       {@link UserMessage} prefix, followed by the recent messages.</li>
 * </ol>
 * <p>
 * The summary is <strong>not persisted</strong> to the store — it is injected
 * at read time and cached in-memory. Caching is keyed by (sessionId, messageCount)
 * to avoid regenerating the summary on every turn when no new messages arrive.
 * <p>
 * <strong>Error handling:</strong> If the summarization LLM call fails, the provider
 * falls back to simple truncation (returning only the {@code keepRecentCount} most
 * recent messages without a summary).
 */
public class SummarizingMemoryProvider implements ChatMemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(SummarizingMemoryProvider.class);

    private static final String SUMMARIZE_PROMPT = """
        Summarize the following customer service conversation concisely.
        Include: (1) key topics discussed, (2) important facts and answers,
        (3) any user preferences or context that would help continue the conversation,
        (4) any pending questions or actions.
        Keep the summary under 200 words. Write the summary in the same language as the conversation.

        Conversation:
        %s

        Summary:""";

    private final ChatMemoryStore store;
    private final ChatModel summaryModel;
    private final int maxBeforeSummary;
    private final int keepRecentCount;

    /** Tracks the last message count at which we generated a summary, per session. */
    private final Map<Object, Integer> lastSummarizedAt = new ConcurrentHashMap<>();

    /** Cached summary text per session. */
    private final Map<Object, String> summaryCache = new ConcurrentHashMap<>();

    public SummarizingMemoryProvider(ChatMemoryStore store, ChatModel summaryModel,
                                     int maxBeforeSummary, int keepRecentCount) {
        this.store = store;
        this.summaryModel = summaryModel;
        this.maxBeforeSummary = maxBeforeSummary;
        this.keepRecentCount = keepRecentCount;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return new SummaryAwareChatMemory(memoryId);
    }

    /**
     * Returns a cached summary if the message count hasn't changed since last
     * summarization; otherwise generates a new summary via the LLM.
     */
    private String getOrGenerateSummary(Object memoryId, int currentCount, List<ChatMessage> oldest) {
        Integer lastCount = lastSummarizedAt.get(memoryId);
        if (lastCount != null && lastCount == currentCount) {
            String cached = summaryCache.get(memoryId);
            if (cached != null) {
                log.debug("[SummarizingMemory] Cache hit for session {}, count={}", memoryId, currentCount);
                return cached;
            }
        }

        log.info("[SummarizingMemory] Generating summary for session {} — {} old messages",
            memoryId, oldest.size());

        try {
            String conversationText = buildConversationText(oldest);
            String prompt = String.format(SUMMARIZE_PROMPT, conversationText);
            String summary = summaryModel.chat(prompt);

            lastSummarizedAt.put(memoryId, currentCount);
            summaryCache.put(memoryId, summary);
            log.info("[SummarizingMemory] Summary generated ({} chars), session {}", summary.length(), memoryId);
            return summary;
        } catch (Exception e) {
            log.warn("[SummarizingMemory] Summarization failed, falling back to truncation: {}", e.getMessage());
            return "（对话摘要生成失败，仅保留最近对话）";
        }
    }

    /** Builds a plain-text representation of messages for the summarization prompt. */
    private String buildConversationText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            // Skip system messages — system prompts and injected summaries should
            // not be summarized themselves, only the actual conversation.
            if (msg.type() == ChatMessageType.SYSTEM) {
                continue;
            }
            String role = switch (msg.type()) {
                case USER -> "用户";
                case AI -> "客服";
                case TOOL_EXECUTION_RESULT -> "工具结果";
                default -> msg.type().name();
            };
            sb.append(role).append(": ").append(msg).append("\n");
        }
        return sb.toString();
    }

    // ── Inner: ChatMemory implementation ─────────────────────────────────

    /**
     * Custom {@link ChatMemory} that delegates persistence to the underlying store
     * and injects a summary prefix in {@link #messages()} on every read.
     * <p>
     * {@link #messages()} always reads the latest messages from the store,
     * so newly added messages are reflected immediately. The summary is
     * injected at read time and is <strong>not</strong> persisted.
     */
    private class SummaryAwareChatMemory implements ChatMemory {
        private final Object memoryId;

        SummaryAwareChatMemory(Object memoryId) {
            this.memoryId = memoryId;
        }

        @Override
        public Object id() {
            return memoryId;
        }

        @Override
        public List<ChatMessage> messages() {
            List<ChatMessage> allMessages = store.getMessages(memoryId);

            if (allMessages.size() <= maxBeforeSummary) {
                return allMessages;
            }

            // Split: oldest → summary, recent → keep intact
            int splitPoint = Math.max(0, allMessages.size() - keepRecentCount);
            List<ChatMessage> oldest = allMessages.subList(0, splitPoint);
            List<ChatMessage> recent = new ArrayList<>(allMessages.subList(splitPoint, allMessages.size()));

            String summary = getOrGenerateSummary(memoryId, allMessages.size(), oldest);

            // Prepend summary as a UserMessage so it blends as conversation context,
            // not system instructions (avoids mixing with @SystemMessage prompt).
            List<ChatMessage> result = new ArrayList<>();
            result.add(UserMessage.from("【对话历史摘要】" + summary));
            result.addAll(recent);
            return result;
        }

        @Override
        public void add(ChatMessage message) {
            List<ChatMessage> all = new ArrayList<>(store.getMessages(memoryId));
            all.add(message);
            store.updateMessages(memoryId, all);

            // Invalidate summary cache since new messages arrived
            lastSummarizedAt.remove(memoryId);
            summaryCache.remove(memoryId);
        }

        @Override
        public void clear() {
            store.deleteMessages(memoryId);
            lastSummarizedAt.remove(memoryId);
            summaryCache.remove(memoryId);
        }
    }
}
