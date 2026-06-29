package com.shopai.agent.integration.memory;

import com.shopai.agent.config.TestConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the summarizing memory provider.
 * <p>
 * Verifies that when message count exceeds the threshold, older messages
 * are summarized and injected as a system prefix while recent messages
 * are preserved. These tests call the REAL LLM for summarization.
 * <p>
 * Requirements: {@code DEEPSEEK_API_KEY} environment variable must be set.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@TestPropertySource(properties = {
    "shopai.agent.memory.mode=summarizing",
    "shopai.agent.memory.summarizing.max-messages-before-summary=8",
    "shopai.agent.memory.summarizing.keep-recent-count=4"
})
@Tag("integration")
@DisplayName("Summarization Memory Integration Tests")
class SummarizationTest {

    @Autowired
    private ChatMemoryProvider memoryProvider;

    @Autowired
    private ChatMemoryStore memoryStore;

    @Autowired
    private JdbcTemplate jdbc;

    private String sessionId;

    @BeforeEach
    void setUp() {
        sessionId = "sum-test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM agent_message WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM conversation WHERE session_id = ?", sessionId);
    }

    @Test
    @DisplayName("Should NOT summarize when message count is below threshold")
    void shouldNotSummarizeBelowThreshold() {
        // Given: 4 messages (< threshold of 8 from test config)
        List<ChatMessage> messages = List.of(
            UserMessage.from("问题1"),
            AiMessage.from("回答1"),
            UserMessage.from("问题2"),
            AiMessage.from("回答2")
        );
        memoryStore.updateMessages(sessionId, messages);

        // When
        ChatMemory memory = memoryProvider.get(sessionId);

        // Then: all messages present, no summary prefix
        List<ChatMessage> result = memory.messages();
        assertThat(result).hasSize(4);

        boolean hasSummary = result.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> ((UserMessage) m).singleText())
            .anyMatch(text -> text.startsWith("【对话历史摘要】"));
        assertThat(hasSummary).isFalse();
    }

    @Test
    @DisplayName("Should summarize when message count exceeds threshold")
    void shouldSummarizeAboveThreshold() {
        // Given: 12 messages (> threshold of 8 from test config)
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            messages.add(UserMessage.from("问题" + i + "：请告诉我关于产品" + i + "的信息"));
            messages.add(AiMessage.from("回答" + i + "：产品" + i + "是ShopAI的热门商品，拥有良好的用户评价。"));
        }
        memoryStore.updateMessages(sessionId, messages);

        // When
        ChatMemory memory = memoryProvider.get(sessionId);

        // Then: compression occurred (summary injected + recent messages)
        List<ChatMessage> result = memory.messages();

        // Should be less than original 12 (compression)
        assertThat(result.size()).isLessThan(12);

        // First message should be a UserMessage with summary prefix
        boolean hasSummaryPrefix = result.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> ((UserMessage) m).singleText())
            .anyMatch(text -> text.startsWith("【对话历史摘要】"));
        assertThat(hasSummaryPrefix)
            .as("Should inject a summary prefix message")
            .isTrue();
    }

    @Test
    @DisplayName("Should preserve recent messages after summarization")
    void shouldPreserveRecentMessages() {
        // Given: 12 messages
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            messages.add(UserMessage.from("第" + i + "轮问题"));
            messages.add(AiMessage.from("第" + i + "轮回答"));
        }
        // Make the last message unique so we can identify it
        String uniqueLastUser = "最后一轮的关键问题：我的订单号是XYZ999";
        messages.set(messages.size() - 2, UserMessage.from(uniqueLastUser));

        memoryStore.updateMessages(sessionId, messages);

        // When
        ChatMemory memory = memoryProvider.get(sessionId);

        // Then: recent messages should still be present
        List<ChatMessage> result = memory.messages();
        boolean recentPreserved = result.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> ((UserMessage) m).singleText())
            .anyMatch(text -> text.contains("XYZ999"));
        assertThat(recentPreserved)
            .as("Recent message with unique identifier should be preserved")
            .isTrue();
    }

    @Test
    @DisplayName("Should return identical messages on repeated calls (caching)")
    void shouldReturnSameResultOnRepeatedCalls() {
        // Given: 10 messages (triggers summarization with threshold=8)
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            messages.add(UserMessage.from("Q" + i));
            messages.add(AiMessage.from("A" + i));
        }
        memoryStore.updateMessages(sessionId, messages);

        // When: two consecutive reads without new messages
        ChatMemory memory1 = memoryProvider.get(sessionId);
        ChatMemory memory2 = memoryProvider.get(sessionId);

        // Then: both should have same messages (cached)
        assertThat(memory2.messages()).hasSize(memory1.messages().size());
    }

    @Test
    @DisplayName("Should handle empty conversation gracefully")
    void shouldHandleEmptyConversation() {
        // Given: no messages for this session
        // When
        ChatMemory memory = memoryProvider.get(sessionId);

        // Then: should return empty without error
        assertThat(memory.messages()).isEmpty();
    }
}
