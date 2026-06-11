package com.shopai.agent.domain;

import java.util.Map;
import java.util.function.Function;

public record ToolDefinition(
    String name,
    String description,
    ToolParameters parameters,
    Function<Map<String, Object>, ToolResult> handler
) {}
