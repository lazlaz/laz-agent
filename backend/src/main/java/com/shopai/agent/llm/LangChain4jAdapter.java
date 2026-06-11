package com.shopai.agent.llm;

import com.shopai.agent.domain.*;
import com.shopai.agent.engine.LlmResponseParser;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

public class LangChain4jAdapter implements LlmAdapter {

    private final ChatLanguageModel model;
    private final LlmResponseParser parser = new LlmResponseParser();

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
        return parser.parse(rawResponse);
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
}
