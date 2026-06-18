package com.shopai.agent.llm;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;

/**
 * Factory for LangChain4j streaming chat models.
 * Uses OpenAiStreamingChatModel which is compatible with DeepSeek's OpenAI-compatible API.
 */
public class LangChain4jAdapter {

    private LangChain4jAdapter() {}

    public static StreamingChatModel createStreamingModel(
        String apiKey, String modelName, String baseUrl, Duration timeout) {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .timeout(timeout)
            .httpClientBuilder(JdkHttpClient.builder())
            .logRequests(true)
            .logResponses(true)
            .build();
    }
}
