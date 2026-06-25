package com.shopai.agent.web;

import com.shopai.agent.engine.PlanExecuteEngine;
import com.shopai.agent.engine.PlanExecuteEvent;
import com.shopai.agent.engine.PlanStep;
import com.shopai.agent.engine.ReActAgentEngine;
import com.shopai.agent.engine.StepResult;
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
    private final PlanExecuteEngine planExecuteEngine;
    private final Map<String, PendingStream> streamStore = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public ChatController(ReActAgentEngine engine, PlanExecuteEngine planExecuteEngine) {
        this.engine = engine;
        this.planExecuteEngine = planExecuteEngine;
    }

    /**
     * Phase 1: Accept user message, return stream URL for SSE connection.
     * Supports {@code mode} field: "react" (default) or "plan-execute".
     */
    @PostMapping("/chat/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());
        String message = body.get("message");
        String mode = body.getOrDefault("mode", "react");
        String messageId = UUID.randomUUID().toString();

        if (message == null || message.isBlank()) {
            return Map.of("error", "Message is required");
        }

        streamStore.put(messageId, new PendingStream(sessionId, message, mode));

        return Map.of(
            "messageId", messageId,
            "streamUrl", "/api/chat/stream/" + messageId
        );
    }

    /**
     * Phase 2: SSE streaming endpoint. Routes to ReAct or Plan-Execute engine based on mode.
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

        if ("plan-execute".equals(pending.mode())) {
            executor.submit(() -> runPlanExecute(pending, emitter, messageId));
        } else {
            executor.submit(() -> runReAct(pending, emitter, messageId));
        }

        return emitter;
    }

    // ── ReAct mode (existing behavior) ──────────────────────────────────

    private void runReAct(PendingStream pending, SseEmitter emitter, String messageId) {
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
            sendErrorAndComplete(emitter, "抱歉，处理您的请求时出错：" + e.getMessage());
        }
    }

    // ── Plan-Execute mode ───────────────────────────────────────────────

    private void runPlanExecute(PendingStream pending, SseEmitter emitter, String messageId) {
        try {
            long startTime = System.currentTimeMillis();
            StringBuilder fullContent = new StringBuilder();

            planExecuteEngine.execute(pending.sessionId(), pending.message(), event -> {
                try {
                    switch (event) {
                        case PlanExecuteEvent.PlanStart __ -> {
                            emitter.send(SseEmitter.event()
                                .name("plan_start")
                                .data(Map.of("phase", "planning")));
                        }

                        case PlanExecuteEvent.PlanReady(var plan) -> {
                            emitter.send(SseEmitter.event()
                                .name("plan")
                                .data(Map.of("steps", plan.steps().stream()
                                    .map(ChatController::stepToMap).toList())));
                        }

                        case PlanExecuteEvent.StepStart(int idx, PlanStep step) -> {
                            emitter.send(SseEmitter.event()
                                .name("step_start")
                                .data(Map.of(
                                    "stepIndex", idx,
                                    "type", step.type(),
                                    "tool", step.tool() != null ? step.tool() : "",
                                    "description", step.description()
                                )));
                        }

                        case PlanExecuteEvent.StepDone(int idx, StepResult result) -> {
                            emitter.send(SseEmitter.event()
                                .name("step")
                                .data(Map.of(
                                    "stepIndex", idx,
                                    "type", result.type(),
                                    "tool", result.tool() != null ? result.tool() : "",
                                    "output", result.output(),
                                    "success", result.success()
                                )));
                        }

                        case PlanExecuteEvent.SynthesisStart __ -> {
                            emitter.send(SseEmitter.event()
                                .name("synthesis")
                                .data(Map.of()));
                        }

                        case PlanExecuteEvent.SynthesisToken(String token) -> {
                            fullContent.append(token);
                            emitter.send(SseEmitter.event()
                                .name("token")
                                .data(token));
                        }

                        case PlanExecuteEvent.SynthesisDone(String content, int inTok, int outTok) -> {
                            long latency = System.currentTimeMillis() - startTime;
                            emitter.send(SseEmitter.event()
                                .name("done")
                                .data(Map.of(
                                    "messageId", messageId,
                                    "content", content,
                                    "tokenUsage", Map.of(
                                        "inputTokens", inTok,
                                        "outputTokens", outTok
                                    ),
                                    "latencyMs", latency
                                )));
                            emitter.complete();
                        }

                        case PlanExecuteEvent.PlanError(String phase, String msg) -> {
                            log.error("[PlanExecute] Error in phase {}: {}", phase, msg);
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("phase", phase, "message", msg)));
                            emitter.complete();
                        }
                    }
                } catch (IOException e) {
                    log.warn("[PlanExecute] Failed to send SSE event: {}", e.getMessage());
                    emitter.completeWithError(e);
                }
            });

        } catch (Exception e) {
            log.error("[PlanExecute] Engine failure", e);
            sendErrorAndComplete(emitter, "抱歉，处理您的请求时出错：" + e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                .name("error")
                .data(Map.of("message", message)));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    /** Serializes a PlanStep to a Map for SSE JSON output. */
    private static Map<String, Object> stepToMap(PlanStep step) {
        return Map.of(
            "index", step.index(),
            "type", step.type(),
            "description", step.description(),
            "tool", step.tool() != null ? step.tool() : "",
            "args", step.args() != null ? step.args() : Map.of()
        );
    }

    private record PendingStream(String sessionId, String message, String mode) {}
}
