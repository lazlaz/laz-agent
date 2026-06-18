package com.shopai.agent.config;

import com.shopai.agent.engine.ShopAiAgent;
import com.shopai.agent.llm.LangChain4jAdapter;
import com.shopai.agent.memory.H2MemoryManager;
import com.shopai.agent.tool.CalculatorTool;
import com.shopai.agent.tool.OrderQueryTool;
import com.shopai.agent.tool.ProductSearchTool;
import com.shopai.agent.tracing.OtelChatModelListener;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.List;

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

    @Value("${shopai.agent.max-history-messages:20}")
    private int maxHistoryMessages;

    // ── OpenTelemetry tracing ────────────────────────────────────────

    @Bean
    public OtelChatModelListener otelChatModelListener(Tracer tracer) {
        return new OtelChatModelListener(tracer);
    }

    // ── Chat model ───────────────────────────────────────────────────

    @Bean
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        return LangChain4jAdapter.createStreamingModel(
            apiKey,
            model,
            baseUrl,
            Duration.parse("PT" + timeout.replace("s", "S").replace("m", "M")),
            listeners
        );
    }

    @Bean
    public ChatMemoryStore chatMemoryStore(JdbcTemplate jdbc) {
        return new H2MemoryManager(jdbc);
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
            .chatMemoryStore(store)
            .maxMessages(maxHistoryMessages)
            .build();
    }

    @Bean
    public ShopAiAgent shopAiAgent(
        StreamingChatModel streamingModel,
        ChatMemoryProvider memoryProvider,
        ProductSearchTool productSearch,
        OrderQueryTool orderQuery,
        CalculatorTool calculator) {
        return AiServices.builder(ShopAiAgent.class)
            .streamingChatModel(streamingModel)
            .chatMemoryProvider(memoryProvider)
            .tools(productSearch, orderQuery, calculator)
            .build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("GET", "POST", "DELETE", "OPTIONS");
            }
        };
    }
}
