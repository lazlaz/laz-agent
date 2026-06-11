package com.shopai.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.domain.*;

import java.util.Map;

public class LlmResponseParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public LlmResponse parse(String rawResponse) {
        String content = rawResponse.trim();
        String thought = extractSection(content, "THOUGHT:");
        String action = extractSection(content, "ACTION:");

        if (action != null && !action.trim().equalsIgnoreCase("FINAL")) {
            ToolCall toolCall = parseToolCall(action.trim());
            return new LlmResponse(content, DecisionType.TOOL_CALL, toolCall, estimateTokens(content));
        } else if (action != null) {
            String answer = extractFinalAnswer(content);
            return new LlmResponse(answer, DecisionType.FINAL, null, estimateTokens(content));
        } else {
            return new LlmResponse(content, DecisionType.THOUGHT, null, estimateTokens(content));
        }
    }

    private String extractSection(String text, String marker) {
        int start = text.indexOf(marker);
        if (start == -1) return null;
        int contentStart = start + marker.length();
        int nextThought = text.indexOf("THOUGHT:", contentStart);
        int nextAction = text.indexOf("ACTION:", contentStart);
        int end = text.length();
        if (nextThought > contentStart) end = Math.min(end, nextThought);
        if (nextAction > contentStart) end = Math.min(end, nextAction);
        return text.substring(contentStart, end).trim();
    }

    private ToolCall parseToolCall(String actionText) {
        int parenIdx = actionText.indexOf('(');
        if (parenIdx == -1) return new ToolCall(actionText.trim(), Map.of());
        String toolName = actionText.substring(0, parenIdx).trim();
        String argsJson = actionText.substring(parenIdx + 1, actionText.lastIndexOf(')')).trim();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);
            return new ToolCall(toolName, args);
        } catch (Exception e) {
            return new ToolCall(toolName, Map.of("_raw", argsJson));
        }
    }

    private String extractFinalAnswer(String text) {
        int lastFINAL = text.lastIndexOf("FINAL");
        if (lastFINAL == -1) return text;
        String after = text.substring(lastFINAL + 5).trim();
        int nextThought = after.indexOf("THOUGHT:");
        int nextAction = after.indexOf("ACTION:");
        int cutoff = after.length();
        if (nextThought >= 0) cutoff = Math.min(cutoff, nextThought);
        if (nextAction >= 0) cutoff = Math.min(cutoff, nextAction);
        return after.substring(0, cutoff).trim();
    }

    private TokenUsage estimateTokens(String text) {
        int estimated = Math.max(1, text.length() / 4);
        return new TokenUsage(estimated, estimated);
    }
}
