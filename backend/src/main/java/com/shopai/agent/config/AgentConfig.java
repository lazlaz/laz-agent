package com.shopai.agent.config;

import com.shopai.agent.llm.LangChain4jAdapter;
import com.shopai.agent.llm.LlmAdapter;
import com.shopai.agent.memory.H2MemoryManager;
import com.shopai.agent.memory.MemoryManager;
import com.shopai.agent.prompt.MustachePromptEngine;
import com.shopai.agent.prompt.PromptEngine;
import com.shopai.agent.tool.CalculatorTool;
import com.shopai.agent.tool.DefaultToolRegistry;
import com.shopai.agent.tool.OrderQueryTool;
import com.shopai.agent.tool.ProductSearchTool;
import com.shopai.agent.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Bean
    public MemoryManager memoryManager(JdbcTemplate jdbc) {
        return new H2MemoryManager(jdbc);
    }

    @Bean
    public ToolRegistry toolRegistry(OrderQueryTool orderQuery, ProductSearchTool productSearch, CalculatorTool calculator) {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(orderQuery.definition());
        registry.register(productSearch.definition());
        registry.register(calculator.definition());
        return registry;
    }
}
