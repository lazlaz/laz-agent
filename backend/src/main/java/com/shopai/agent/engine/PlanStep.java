package com.shopai.agent.engine;

import java.util.Map;

/** A single step in an execution plan — either a tool call or a reasoning pause. */
public record PlanStep(
    int index,
    String type,        // "tool_call" | "reasoning"
    String description, // Human-readable description (shown in UI)
    String tool,        // Tool method name, e.g. "searchProducts"
    Map<String, Object> args // Arguments for tool_call steps; empty for reasoning
) {
    public PlanStep {
        if (!"tool_call".equals(type) && !"reasoning".equals(type)) {
            throw new IllegalArgumentException("Step type must be 'tool_call' or 'reasoning', got: " + type);
        }
    }
}
