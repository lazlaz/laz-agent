package com.shopai.agent.domain;

public record LlmResponse(
    String content,
    DecisionType decision,
    ToolCall toolCall,
    TokenUsage usage
) {}
