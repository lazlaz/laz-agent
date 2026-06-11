package com.shopai.agent.llm;

import com.shopai.agent.domain.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

public class LangChain4jAdapter implements LlmAdapter {

    private final ChatLanguageModel model;

    public LangChain4jAdapter(String apiKey, String modelName, String baseUrl, Duration timeout) {
        this.model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .timeout(timeout)
            .build();
    }

    @Override
    public LlmResponse chat(ChatRequest request) {
        String prompt = buildFullPrompt(request);
        String rawResponse = model.generate(prompt);
        return parseResponse(rawResponse);
    }

    private String buildFullPrompt(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.systemPrompt()).append("\n\n");

        if (request.messages() != null) {
            for (Message msg : request.messages()) {
                sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        }

        return sb.toString();
    }

    LlmResponse parseResponse(String raw) {
        String content = raw.trim();
        String thought = extractSection(content, "THOUGHT:");
        String action = extractSection(content, "ACTION:");

        if (action != null && !action.trim().equalsIgnoreCase("FINAL")) {
            ToolCall toolCall = parseToolCall(action.trim());
            return new LlmResponse(content, DecisionType.TOOL_CALL, toolCall, estimateTokens(content));
        } else if (action != null && action.trim().equalsIgnoreCase("FINAL")) {
            String answer = extractAfterFinal(content);
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
        if (parenIdx == -1) return new ToolCall(actionText.trim(), java.util.Map.of());

        String toolName = actionText.substring(0, parenIdx).trim();
        String argsJson = actionText.substring(parenIdx + 1, actionText.lastIndexOf(')')).trim();

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            var args = mapper.readValue(argsJson, java.util.Map.class);
            return new ToolCall(toolName, args);
        } catch (Exception e) {
            return new ToolCall(toolName, java.util.Map.of());
        }
    }

    private String extractAfterFinal(String text) {
        int finalIdx = text.lastIndexOf("FINAL");
        if (finalIdx == -1) return text;
        String after = text.substring(finalIdx + 5).trim();
        int lastAction = after.lastIndexOf("ACTION:");
        if (lastAction != -1) {
            after = after.substring(lastAction + 7).trim();
            if (after.equalsIgnoreCase("FINAL") || after.isBlank()) {
                after = text.substring(text.lastIndexOf("FINAL") + 5).trim();
            }
        }
        return after;
    }

    private TokenUsage estimateTokens(String text) {
        int estimated = Math.max(1, text.length() / 4);
        return new TokenUsage(estimated, estimated);
    }
}
