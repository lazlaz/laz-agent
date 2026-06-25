package com.shopai.agent.engine;

/** The result of executing a single plan step. */
public record StepResult(
    int stepIndex,
    String type,    // "tool_call" | "reasoning"
    String tool,    // Tool name (null for reasoning)
    String output,  // Tool result text or reasoning description
    boolean success
) {}
