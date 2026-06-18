package com.shopai.agent.web;

import com.shopai.agent.engine.ReActAgentEngine;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ReActAgentEngine engine;
    private final Map<String, PendingStream> streamStore = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public ChatController(ReActAgentEngine engine) {
        this.engine = engine;
    }

    /**
     * Phase 1: Accept user message, return stream URL for SSE connection.
     */
    @PostMapping("/chat/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());
        String message = body.get("message");
        String messageId = UUID.randomUUID().toString();

        if (message == null || message.isBlank()) {
            return Map.of("error", "Message is required");
        }

        // Store pending stream info for phase 2
        streamStore.put(messageId, new PendingStream(sessionId, message));

        return Map.of(
            "messageId", messageId,
            "streamUrl", "/api/chat/stream/" + messageId
        );
    }

    /**
     * Phase 2: SSE streaming endpoint using LangChain4j TokenStream.
     */
    @GetMapping(value = "/chat/stream/{messageId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String messageId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        PendingStream pending = streamStore.remove(messageId);
        if (pending == null) {
            executor.submit(() -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "No pending request found for messageId: " + messageId)));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        executor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();
                StringBuilder fullContent = new StringBuilder();

                TokenStream tokenStream = engine.stream(pending.sessionId(), pending.message());

                tokenStream
                    .onPartialResponse(token -> {
                        try {
                            fullContent.append(token);
                            emitter.send(SseEmitter.event()
                                .name("token")
                                .data(token));
                        } catch (IOException e) {
                            log.warn("Failed to send token: {}", e.getMessage());
                        }
                    })
                    .onCompleteResponse(response -> {
                        try {
                            long latency = System.currentTimeMillis() - startTime;
                            var usage = response.metadata().tokenUsage();
                            int inputTokens = (usage != null && usage.inputTokenCount() != null) ? usage.inputTokenCount() : 0;
                            int outputTokens = (usage != null && usage.outputTokenCount() != null) ? usage.outputTokenCount() : 0;

                            emitter.send(SseEmitter.event()
                                .name("done")
                                .data(Map.of(
                                    "messageId", messageId,
                                    "content", fullContent.toString(),
                                    "tokenUsage", Map.of(
                                        "inputTokens", inputTokens,
                                        "outputTokens", outputTokens
                                    ),
                                    "latencyMs", latency
                                )));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onError(error -> {
                        log.error("Agent streaming error", error);
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("message", error.getMessage() != null ? error.getMessage() : "Unknown error")));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .start();

            } catch (Exception e) {
                log.error("Failed to start agent streaming", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "抱歉，处理您的请求时出错：" + e.getMessage())));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    private record PendingStream(String sessionId, String message) {}
}
