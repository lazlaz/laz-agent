package com.shopai.agent.domain;

public record StepRecord(
    int iteration,
    StepType type,
    String thought,
    ToolCall toolCall,
    ToolResult toolResult
) {
    public static StepRecord thought(int iteration, String thought) {
        return new StepRecord(iteration, StepType.THOUGHT, thought, null, null);
    }

    public static StepRecord toolCall(int iteration, ToolCall call) {
        return new StepRecord(iteration, StepType.TOOL_CALL, null, call, null);
    }

    public static StepRecord toolResult(int iteration, ToolResult result) {
        return new StepRecord(iteration, StepType.TOOL_RESULT, null, null, result);
    }

    public static StepRecord finalAnswer(int iteration, String answer) {
        return new StepRecord(iteration, StepType.FINAL, answer, null, null);
    }
}
