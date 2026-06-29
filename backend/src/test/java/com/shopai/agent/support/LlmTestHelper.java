package com.shopai.agent.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Static helpers for LLM-aware integration tests.
 */
public class LlmTestHelper {

    private static final Logger log = LoggerFactory.getLogger(LlmTestHelper.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_RETRIES = 3;

    private LlmTestHelper() {}

    // ── TokenStream → String ────────────────────────────────────────────

    /**
     * Collects all tokens from a {@link TokenStream} into a single String,
     * blocking until the stream completes or times out.
     */
    public static String collectStream(TokenStream tokenStream, Duration timeout) {
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder sb = new StringBuilder();

        tokenStream.onPartialResponse(sb::append)
            .onCompleteResponse(response -> future.complete(sb.toString()))
            .onError(future::completeExceptionally)
            .start();

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (!sb.isEmpty()) {
                log.warn("Stream timed out, returning partial content ({} chars)", sb.length());
                return sb.toString();
            }
            throw new RuntimeException("TokenStream failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience overload with 60s default timeout.
     */
    public static String collectStream(TokenStream tokenStream) {
        return collectStream(tokenStream, DEFAULT_TIMEOUT);
    }

    // ── Tool call extraction ────────────────────────────────────────────

    /**
     * Extracts all tool execution requests from the AI messages in a {@link ChatMemoryStore}
     * for the given session. Useful for verifying tool selection in ReAct mode tests.
     */
    public static List<dev.langchain4j.agent.tool.ToolExecutionRequest> extractToolCalls(
        ChatMemoryStore store, String sessionId) {
        List<ChatMessage> messages = store.getMessages(sessionId);
        return messages.stream()
            .filter(m -> m instanceof AiMessage)
            .flatMap(m -> {
                AiMessage ai = (AiMessage) m;
                return ai.toolExecutionRequests() != null
                    ? ai.toolExecutionRequests().stream()
                    : java.util.stream.Stream.empty();
            })
            .toList();
    }

    // ── Retry ───────────────────────────────────────────────────────────

    /**
     * Retries a supplier until the condition is met or max retries exhausted.
     * Exponential backoff: delay × attempt.
     */
    @SuppressWarnings("unchecked")
    public static <T> T retryUntil(Supplier<T> supplier, Predicate<T> condition,
                                    int maxRetries, Duration delay) {
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                T result = supplier.get();
                if (condition.test(result)) {
                    return result;
                }
                log.info("Retry {}/{}: condition not met, retrying in {}ms",
                    i + 1, maxRetries, delay.toMillis() * (i + 1));
            } catch (Exception e) {
                lastException = e;
                log.warn("Retry {}/{} failed: {}", i + 1, maxRetries, e.getMessage());
            }
            sleep(delay.multipliedBy(i + 1));
        }
        if (lastException != null) {
            throw new AssertionError("Failed after " + maxRetries + " retries", lastException);
        }
        throw new AssertionError("Condition not met after " + maxRetries + " retries");
    }

    /** Convenience overload with default 3 retries, 2s delay. */
    public static <T> T retryUntil(Supplier<T> supplier, Predicate<T> condition) {
        return retryUntil(supplier, condition, DEFAULT_RETRIES, Duration.ofSeconds(2));
    }

    // ── Keyword assertions ──────────────────────────────────────────────

    /**
     * Asserts that at least {@code threshold * 100}% of expected keywords appear in the text.
     */
    public static boolean containsKeywords(String text, List<String> keywords, double threshold) {
        if (keywords == null || keywords.isEmpty()) return true;
        long matched = keywords.stream().filter(text::contains).count();
        double rate = (double) matched / keywords.size();
        log.info("Keyword match: {}/{} ({:.0f}%), threshold={:.0f}%",
            matched, keywords.size(), rate * 100, threshold * 100);
        return rate >= threshold;
    }

    /** Convenience overload with 80% threshold. */
    public static boolean containsKeywords(String text, List<String> keywords) {
        return containsKeywords(text, keywords, 0.80);
    }

    /**
     * Asserts that NONE of the forbidden keywords appear in the text.
     */
    public static boolean containsNoForbiddenKeywords(String text, List<String> forbidden) {
        if (forbidden == null || forbidden.isEmpty()) return true;
        List<String> found = forbidden.stream().filter(text::contains).toList();
        if (!found.isEmpty()) {
            log.warn("Forbidden keywords found: {}", found);
            return false;
        }
        return true;
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
