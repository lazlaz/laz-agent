package com.shopai.agent.integration.memory;

import com.shopai.agent.config.TestConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for multi-turn conversation memory via H2.
 * <p>
 * Uses the real {@link ChatMemoryStore} backed by in-memory H2.
 * No external dependencies required.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("integration")
@DisplayName("Multi-Turn Memory Integration Tests")
class MultiTurnMemoryTest {

    @Autowired
    private ChatMemoryStore memoryStore;

    @Autowired
    private JdbcTemplate jdbc;

    private String sessionId;

    @BeforeEach
    void setUp() {
        sessionId = "mem-test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM agent_message WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM conversation WHERE session_id = ?", sessionId);
    }

    @Test
    @DisplayName("Should store and retrieve messages in insertion order")
    void shouldStoreAndRetrieveMessagesInOrder() {
        // Given: two turns of conversation
        List<ChatMessage> messages = List.of(
            UserMessage.from("我的名字是王小明"),
            AiMessage.from("你好王小明，有什么可以帮助你的？"),
            UserMessage.from("帮我查一下我的订单"),
            AiMessage.from("请提供您的订单号")
        );
        memoryStore.updateMessages(sessionId, messages);

        // When
        List<ChatMessage> retrieved = memoryStore.getMessages(sessionId);

        // Then
        assertThat(retrieved).hasSize(4);

        // Messages should be in order
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).contains("王小明");

        assertThat(retrieved.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) retrieved.get(1)).text()).contains("王小明");

        assertThat(retrieved.get(2)).isInstanceOf(UserMessage.class);
        assertThat(retrieved.get(3)).isInstanceOf(AiMessage.class);
    }

    @Test
    @DisplayName("Should update messages by replacing all for a session")
    void shouldReplaceMessagesOnUpdate() {
        // Given: initial messages
        memoryStore.updateMessages(sessionId, List.of(
            UserMessage.from("第一轮问题"),
            AiMessage.from("第一轮回答")
        ));

        // When: update with new messages (replaces all)
        memoryStore.updateMessages(sessionId, List.of(
            UserMessage.from("第二轮问题"),
            AiMessage.from("第二轮回答")
        ));

        // Then: only the new messages are present
        List<ChatMessage> retrieved = memoryStore.getMessages(sessionId);
        assertThat(retrieved).hasSize(2);
        assertThat(((UserMessage) retrieved.get(0)).singleText()).contains("第二轮");
    }

    @Test
    @DisplayName("Should return empty list for non-existent session")
    void shouldReturnEmptyForNonExistentSession() {
        List<ChatMessage> messages = memoryStore.getMessages("non-existent-session");
        assertThat(messages).isEmpty();
    }

    @Test
    @DisplayName("Should delete all messages for a session")
    void shouldDeleteSessionMessages() {
        // Given
        memoryStore.updateMessages(sessionId, List.of(
            UserMessage.from("test message"),
            AiMessage.from("test response")
        ));

        // When
        memoryStore.deleteMessages(sessionId);

        // Then
        List<ChatMessage> retrieved = memoryStore.getMessages(sessionId);
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Should handle multiple concurrent sessions independently")
    void shouldIsolateSessions() {
        String sessionA = sessionId + "-A";
        String sessionB = sessionId + "-B";

        memoryStore.updateMessages(sessionA, List.of(
            UserMessage.from("Session A message"),
            AiMessage.from("Session A response")
        ));
        memoryStore.updateMessages(sessionB, List.of(
            UserMessage.from("Session B message"),
            AiMessage.from("Session B response")
        ));

        List<ChatMessage> msgsA = memoryStore.getMessages(sessionA);
        List<ChatMessage> msgsB = memoryStore.getMessages(sessionB);

        assertThat(msgsA).hasSize(2);
        assertThat(((UserMessage) msgsA.get(0)).singleText()).contains("Session A");

        assertThat(msgsB).hasSize(2);
        assertThat(((UserMessage) msgsB.get(0)).singleText()).contains("Session B");
    }

    @Test
    @DisplayName("Should preserve AI message with tool execution requests")
    void shouldPersistToolExecutionRequests() {
        // Given: an AI message that called a tool
        dev.langchain4j.agent.tool.ToolExecutionRequest toolReq =
            dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .id("tool-call-1")
                .name("queryOrders")
                .arguments("{\"orderNo\":\"20240611001\"}")
                .build();

        AiMessage aiWithTool = AiMessage.from(
            "Let me check that order for you",
            List.of(toolReq));

        memoryStore.updateMessages(sessionId, List.of(
            UserMessage.from("查订单20240611001"),
            aiWithTool
        ));

        // When
        List<ChatMessage> retrieved = memoryStore.getMessages(sessionId);

        // Then
        assertThat(retrieved).hasSize(2);
        assertThat(retrieved.get(1)).isInstanceOf(AiMessage.class);

        AiMessage retrievedAi = (AiMessage) retrieved.get(1);
        assertThat(retrievedAi.toolExecutionRequests()).isNotEmpty();
        assertThat(retrievedAi.toolExecutionRequests().get(0).name())
            .isEqualTo("queryOrders");
        assertThat(retrievedAi.toolExecutionRequests().get(0).arguments())
            .contains("20240611001");
    }
}
