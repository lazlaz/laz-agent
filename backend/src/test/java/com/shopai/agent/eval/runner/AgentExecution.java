package com.shopai.agent.eval.runner;

import java.util.List;
import java.util.Map;

/**
 * Result of a single agent execution, containing both the text answer
 * and the tool calls the agent made during execution.
 */
public record AgentExecution(
    String answer,
    List<ToolCall> toolCalls
) {
    /**
     * A single tool invocation captured during agent execution.
     */
    public record ToolCall(
        String toolName,
        Map<String, Object> args
    ) {}

    /** Convenience factory when no tool calls were made. */
    public static AgentExecution of(String answer) {
        return new AgentExecution(answer, List.of());
    }

    /** Convenience factory with tool calls. */
    public static AgentExecution of(String answer, List<ToolCall> toolCalls) {
        return new AgentExecution(answer, toolCalls);
    }
}
