package com.shopai.agent.web;

import com.shopai.agent.domain.AgentRequest;
import com.shopai.agent.domain.AgentResponse;
import com.shopai.agent.domain.StepRecord;
import com.shopai.agent.engine.AgentEngine;
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

    private final AgentEngine engine;
    private final Map<String, AgentResponse> responseStore = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public ChatController(AgentEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/chat/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());
        String message = body.get("message");
        String messageId = UUID.randomUUID().toString();

        executor.submit(() -> {
            try {
                AgentResponse response = engine.execute(new AgentRequest(sessionId, message));
                responseStore.put(messageId, response);
            } catch (Exception e) {
                AgentResponse errorResponse = new AgentResponse(
                    "抱歉，处理您的请求时出错：" + e.getMessage(),
                    java.util.List.of(),
                    com.shopai.agent.domain.TokenUsage.ZERO,
                    0
                );
                responseStore.put(messageId, errorResponse);
            }
        });

        return Map.of(
            "messageId", messageId,
            "streamUrl", "/api/chat/stream/" + messageId
        );
    }

    @GetMapping(value = "/chat/stream/{messageId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String messageId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.submit(() -> {
            try {
                AgentResponse response = null;
                int polls = 0;
                while (response == null && polls < 600) {
                    response = responseStore.get(messageId);
                    if (response == null) {
                        Thread.sleep(100);
                        polls++;
                    }
                }

                if (response == null) {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "Request timed out")));
                    emitter.complete();
                    return;
                }

                for (StepRecord step : response.steps()) {
                    emitter.send(SseEmitter.event()
                        .name("step")
                        .data(stepToMap(step)));
                    Thread.sleep(50);
                }

                emitter.send(SseEmitter.event()
                    .name("final")
                    .data(Map.of(
                        "messageId", messageId,
                        "content", response.content(),
                        "steps", response.steps().stream().map(this::stepToMap).toList(),
                        "tokenUsage", Map.of(
                            "inputTokens", response.totalUsage().inputTokens(),
                            "outputTokens", response.totalUsage().outputTokens()
                        ),
                        "latencyMs", response.latencyMs()
                    )));

                emitter.complete();
                responseStore.remove(messageId);

            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private Map<String, Object> stepToMap(StepRecord step) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("iteration", step.iteration());
        map.put("type", step.type().name());
        if (step.thought() != null) map.put("thought", step.thought());
        if (step.toolCall() != null) {
            map.put("toolName", step.toolCall().name());
            map.put("arguments", step.toolCall().arguments());
        }
        if (step.toolResult() != null) {
            map.put("result", Map.of(
                "success", step.toolResult().success(),
                "content", step.toolResult().content()
            ));
        }
        return map;
    }
}
