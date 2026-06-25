package com.shopai.agent.llm;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.List;

/**
 * Factory for LangChain4j streaming chat models.
 * Uses OpenAiStreamingChatModel which is compatible with DeepSeek's OpenAI-compatible API.
 */
public class LangChain4jAdapter {

    private LangChain4jAdapter() {}

    /**
     * Creates a synchronous {@link ChatModel} for planning and summarization use cases
     * where the caller needs a complete response before proceeding.
     */
    public static ChatModel createChatModel(
        String apiKey, String modelName, String baseUrl, Duration timeout) {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .timeout(timeout)
            .httpClientBuilder(JdkHttpClient.builder())
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    public static StreamingChatModel createStreamingModel(
        String apiKey, String modelName, String baseUrl, Duration timeout) {
        return createStreamingModel(apiKey, modelName, baseUrl, timeout, List.of());
    }

    public static StreamingChatModel createStreamingModel(
        String apiKey, String modelName, String baseUrl, Duration timeout,
        List<ChatModelListener> listeners) {
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .timeout(timeout)
            .httpClientBuilder(JdkHttpClient.builder())
            .listeners(listeners)
            .logRequests(true)
            .logResponses(true)
            .build();
    }
}
