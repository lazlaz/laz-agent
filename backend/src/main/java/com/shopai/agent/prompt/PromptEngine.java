package com.shopai.agent.prompt;

import com.shopai.agent.domain.ChatRequest;
import com.shopai.agent.domain.Message;
import com.shopai.agent.domain.ToolDefinition;

import java.util.List;
import java.util.Map;

public interface PromptEngine {
    ChatRequest build(String templateName, Map<String, Object> vars);

    record BuildVars(
        List<ToolDefinition> tools,
        List<Message> history,
        String userInput
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                "tools", tools,
                "history", history,
                "userInput", userInput
            );
        }
    }
}
