package com.shopai.agent.config;

import com.shopai.agent.llm.LangChain4jAdapter;
import com.shopai.agent.llm.LlmAdapter;
import com.shopai.agent.prompt.MustachePromptEngine;
import com.shopai.agent.prompt.PromptEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgentConfig {

    @Value("${shopai.llm.api-key}")
    private String apiKey;

    @Value("${shopai.llm.model}")
    private String model;

    @Value("${shopai.llm.base-url}")
    private String baseUrl;

    @Value("${shopai.llm.timeout}")
    private String timeout;

    @Bean
    public LlmAdapter llmAdapter() {
        return new LangChain4jAdapter(
            apiKey,
            model,
            baseUrl,
            Duration.parse("PT" + timeout.replace("s", "S").replace("m", "M"))
        );
    }

    @Bean
    public PromptEngine promptEngine() {
        return new MustachePromptEngine();
    }
}
