package com.shopai.agent.engine;

import dev.langchain4j.service.TokenStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages streaming agent sessions using LangChain4j AiServices.
 * Provides session-scoped {@link TokenStream} instances for SSE streaming.
 */
@Component
public class ReActAgentEngine {

    private final ShopAiAgent agent;
    private final Map<String, List<PendingMessage>> pendingMessages = new ConcurrentHashMap<>();

    public ReActAgentEngine(ShopAiAgent agent) {
        this.agent = agent;
    }

    /**
     * Starts a streaming chat and returns a TokenStream.
     * The caller subscribes to the TokenStream for real-time events.
     */
    public TokenStream stream(String sessionId, String userMessage) {
        return agent.chat(sessionId, userMessage);
    }

    /**
     * Records a pending message request for the two-phase (POST → SSE GET) pattern.
     */
    public void enqueue(String messageId, String sessionId, String message) {
        pendingMessages.computeIfAbsent(messageId, k -> new ArrayList<>())
            .add(new PendingMessage(sessionId, message));
    }

    /**
     * Retrieves and removes the pending message for a given message ID.
     */
    public PendingMessage dequeue(String messageId) {
        List<PendingMessage> list = pendingMessages.remove(messageId);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    public record PendingMessage(String sessionId, String message) {}
}
