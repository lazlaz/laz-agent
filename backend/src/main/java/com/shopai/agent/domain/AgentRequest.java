package com.shopai.agent.domain;

import java.util.Map;

public record AgentRequest(
    String sessionId,
    String userInput,
    Map<String, Object> context
) {
    public AgentRequest(String sessionId, String userInput) {
        this(sessionId, userInput, Map.of());
    }
}
