package com.shopai.agent.domain;

import java.util.List;

public record ChatRequest(
    String systemPrompt,
    List<Message> messages,
    List<ToolDefinition> toolsAvailable
) {}
