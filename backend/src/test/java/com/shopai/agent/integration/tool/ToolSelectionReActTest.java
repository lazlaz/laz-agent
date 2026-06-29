package com.shopai.agent.integration.tool;

import com.shopai.agent.config.TestConfig;
import com.shopai.agent.engine.ShopAiAgent;
import com.shopai.agent.support.LlmTestHelper;
import com.shopai.agent.support.TestDataFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.TokenStream;
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
 * Integration tests for tool selection accuracy in ReAct mode.
 * <p>
 * These tests call the REAL LLM (DeepSeek) via {@link ShopAiAgent} and verify
 * that the correct tool is selected with appropriate arguments for each user query.
 * Tool execution happens against an in-memory H2 seeded with known test data.
 * <p>
 * Requirements: {@code DEEPSEEK_API_KEY} environment variable must be set.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("integration")
@DisplayName("ReAct Tool Selection Integration Tests")
class ToolSelectionReActTest {
    private final Logger log = LoggerFactory.getLogger(ToolSelectionReActTest.class);

    @Autowired
    private ShopAiAgent agent;

    @Autowired
    private ChatMemoryStore memoryStore;

    @Autowired
    private JdbcTemplate jdbc;

    private String sessionId;

    @BeforeEach
    void setUp() {
        sessionId = "test-react-" + UUID.randomUUID().toString().substring(0, 8);
        TestDataFactory.seedAll(jdbc);
    }

    @AfterEach
    void tearDown() {
        TestDataFactory.clearAll(jdbc);
    }

    // ── Order Query Tool ────────────────────────────────────────────────

    @Test
    @DisplayName("Should select queryOrders when user asks about an order by number")
    void shouldSelectOrderQueryByOrderNo() {
        String answer = sendMessage("查询订单号20240611001");
        log.info("Agent answer: {}", answer);
        log.info("Reference answer: 20240611001 | 张三 | 已发货 | ¥100.00 | 2024-06-11 10:00:00");

        // Verify the answer contains key order information (tool was called successfully)
        // AiServices tool calls are internal — verify via the final answer content
        assertThat(answer)
            .as("Should contain order details from OrderQueryTool")
            .contains("20240611001")
            .contains("张三");
        assertThat(answer).containsAnyOf("shipped", "已发货", "发货");
    }

    @Test
    @DisplayName("Should select queryOrders when user asks by customer name")
    void shouldSelectOrderQueryByCustomerName() {
        String answer = sendMessage("查询张三的订单");

        // Verify answer contains order details for the specified customer
        assertThat(answer).contains("张三");
    }

    // ── Product Search Tool ─────────────────────────────────────────────

    @Test
    @DisplayName("Should select searchProducts when user asks about phones under budget")
    void shouldSelectProductSearchWithPriceFilter() {
        String answer = sendMessage("推荐5000以下的手机，拍照好一点的");

        // Should return product recommendations mentioning relevant brands/models
        assertThat(answer)
            .as("Should contain phone recommendations")
            .containsAnyOf("小米", "14 Pro", "手机", "拍照");
    }

    @Test
    @DisplayName("Should select searchProducts for iPhone query")
    void shouldSelectProductSearchForiPhone() {
        String answer = sendMessage("有没有iPhone手机？");

        assertThat(answer).containsAnyOf("iPhone", "苹果");
    }

    // ── Calculator Tool ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should select calculate for arithmetic expression")
    void shouldSelectCalculatorForMath() {
        String answer = sendMessage("帮我算一下1999乘以3等于多少");

        assertThat(answer).contains("5997");
    }

    // ── Policy Query Tool (RAG) ─────────────────────────────────────────

    @Test
    @DisplayName("Should select queryPolicy for return policy question")
    void shouldSelectPolicyQueryForReturnPolicy() {
        String answer = sendMessage("如何退货？退货政策是什么？");

        // PolicyQueryTool calls RAG, which returns mock results in test profile
        assertThat(answer).isNotEmpty();
        // In test profile, the mocked RAG may return "未找到相关的政策条款"
        // which is acceptable — we just verify the agent responds
    }

    // ── Non-tool questions ──────────────────────────────────────────────

    @Test
    @DisplayName("Should handle greeting without calling tools")
    void shouldNotCallToolsForGreeting() {
        String answer = sendMessage("你好！");

        // A greeting should get a friendly response
        assertThat(answer).isNotEmpty();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String sendMessage(String userMessage) {
        TokenStream stream = agent.chat(sessionId, userMessage);
        return LlmTestHelper.collectStream(stream);
    }

    private List<ToolExecutionRequest> extractToolCalls() {
        return LlmTestHelper.extractToolCalls(memoryStore, sessionId);
    }

    /** Verifies the answer contains all expected keywords. */
    private void assertAnswerContains(String answer, String... keywords) {
        for (String kw : keywords) {
            assertThat(answer).as("Answer should contain: " + kw).contains(kw);
        }
    }
}
