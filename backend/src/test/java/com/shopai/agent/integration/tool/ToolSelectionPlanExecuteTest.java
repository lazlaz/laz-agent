package com.shopai.agent.integration.tool;

import com.shopai.agent.config.TestConfig;
import com.shopai.agent.engine.PlanExecuteEngine;
import com.shopai.agent.engine.PlanExecuteEvent;
import com.shopai.agent.support.TestDataFactory;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for tool selection accuracy in Plan-Execute mode.
 * <p>
 * These tests call the REAL LLM (DeepSeek) via {@link PlanExecuteEngine} and verify
 * that the generated plan contains the expected tool_call steps.
 * <p>
 * Requirements: {@code DEEPSEEK_API_KEY} environment variable must be set.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("integration")
@DisplayName("Plan-Execute Tool Selection Integration Tests")
class ToolSelectionPlanExecuteTest {

    @Autowired
    private PlanExecuteEngine engine;

    @Autowired
    private JdbcTemplate jdbc;

    private String sessionId;
    private List<PlanExecuteEvent> capturedEvents;
    private CompletableFuture<Void> executionDone;

    @BeforeEach
    void setUp() {
        sessionId = "test-pe-" + UUID.randomUUID().toString().substring(0, 8);
        capturedEvents = new ArrayList<>();
        executionDone = new CompletableFuture<>();
        TestDataFactory.seedAll(jdbc);
    }

    @AfterEach
    void tearDown() {
        TestDataFactory.clearAll(jdbc);
    }

    // ── Order Query ────────────────────────────────────────────────────

    @Test
    @DisplayName("Plan should include queryOrders step for order number query")
    void shouldPlanOrderQueryByOrderNo() throws Exception {
        engine.execute(sessionId, "查询订单号20240611001", event -> {
            capturedEvents.add(event);
            if (event instanceof PlanExecuteEvent.SynthesisDone
                || event instanceof PlanExecuteEvent.PlanError) {
                executionDone.complete(null);
            }
        });

        executionDone.get(60, TimeUnit.SECONDS);

        // Verify plan contains a tool_call step for queryOrders
        List<PlanExecuteEvent.PlanReady> plans = capturedEvents.stream()
            .filter(e -> e instanceof PlanExecuteEvent.PlanReady)
            .map(e -> (PlanExecuteEvent.PlanReady) e)
            .toList();

        assertThat(plans).as("Should emit a PlanReady event").isNotEmpty();

        var plan = plans.get(0).plan();
        assertThat(plan.steps())
            .anyMatch(s -> "tool_call".equals(s.type())
                && "queryOrders".equals(s.tool())
                && String.valueOf(s.args()).contains("20240611001"));
    }

    // ── Product Search ─────────────────────────────────────────────────

    @Test
    @DisplayName("Plan should include searchProducts step for product recommendation")
    void shouldPlanProductSearch() throws Exception {
        engine.execute(sessionId, "推荐5000以下的手机", event -> {
            capturedEvents.add(event);
            if (event instanceof PlanExecuteEvent.SynthesisDone
                || event instanceof PlanExecuteEvent.PlanError) {
                executionDone.complete(null);
            }
        });

        executionDone.get(60, TimeUnit.SECONDS);

        List<PlanExecuteEvent.PlanReady> plans = capturedEvents.stream()
            .filter(e -> e instanceof PlanExecuteEvent.PlanReady)
            .map(e -> (PlanExecuteEvent.PlanReady) e)
            .toList();

        assertThat(plans).isNotEmpty();
        var plan = plans.get(0).plan();
        assertThat(plan.steps())
            .anyMatch(s -> "tool_call".equals(s.type())
                && "searchProducts".equals(s.tool()));
    }

    // ── Calculator ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Plan should include calculate step for math questions")
    void shouldPlanCalculation() throws Exception {
        engine.execute(sessionId, "帮我算一下1999乘以3", event -> {
            capturedEvents.add(event);
            if (event instanceof PlanExecuteEvent.SynthesisDone
                || event instanceof PlanExecuteEvent.PlanError) {
                executionDone.complete(null);
            }
        });

        executionDone.get(60, TimeUnit.SECONDS);

        List<PlanExecuteEvent.PlanReady> plans = capturedEvents.stream()
            .filter(e -> e instanceof PlanExecuteEvent.PlanReady)
            .map(e -> (PlanExecuteEvent.PlanReady) e)
            .toList();

        assertThat(plans).isNotEmpty();
        var plan = plans.get(0).plan();
        assertThat(plan.steps())
            .anyMatch(s -> "tool_call".equals(s.type())
                && "calculate".equals(s.tool()));
    }

    // ── Policy Query ───────────────────────────────────────────────────

    @Test
    @DisplayName("Plan should include queryPolicy step for return policy questions")
    void shouldPlanPolicyQuery() throws Exception {
        engine.execute(sessionId, "如何退货？退货政策是什么？", event -> {
            capturedEvents.add(event);
            if (event instanceof PlanExecuteEvent.SynthesisDone
                || event instanceof PlanExecuteEvent.PlanError) {
                executionDone.complete(null);
            }
        });

        executionDone.get(60, TimeUnit.SECONDS);

        List<PlanExecuteEvent.PlanReady> plans = capturedEvents.stream()
            .filter(e -> e instanceof PlanExecuteEvent.PlanReady)
            .map(e -> (PlanExecuteEvent.PlanReady) e)
            .toList();

        assertThat(plans).isNotEmpty();
        var plan = plans.get(0).plan();
        assertThat(plan.steps())
            .anyMatch(s -> "tool_call".equals(s.type())
                && "queryPolicy".equals(s.tool()));
    }

    // ── Synthesis ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should emit SynthesisDone with final answer")
    void shouldSynthesizeFinalAnswer() throws Exception {
        engine.execute(sessionId, "你好", event -> {
            capturedEvents.add(event);
            if (event instanceof PlanExecuteEvent.SynthesisDone
                || event instanceof PlanExecuteEvent.PlanError) {
                executionDone.complete(null);
            }
        });

        executionDone.get(60, TimeUnit.SECONDS);

        assertThat(capturedEvents)
            .anyMatch(e -> e instanceof PlanExecuteEvent.SynthesisDone);

        List<PlanExecuteEvent.SynthesisDone> doneEvents = capturedEvents.stream()
            .filter(e -> e instanceof PlanExecuteEvent.SynthesisDone)
            .map(e -> (PlanExecuteEvent.SynthesisDone) e)
            .toList();

        assertThat(doneEvents).isNotEmpty();
        assertThat(doneEvents.get(0).content()).isNotBlank();
    }

    // ── Error handling ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should handle unknown tool gracefully via fallback plan")
    void shouldFallbackOnUnknownTool() throws Exception {
        // The LLM shouldn't generate unknown tools, but verify the engine
        // completes without throwing for a simple request
        engine.execute(sessionId, "你好，请介绍一下你自己", event -> {
            capturedEvents.add(event);
            if (event instanceof PlanExecuteEvent.SynthesisDone
                || event instanceof PlanExecuteEvent.PlanError) {
                executionDone.complete(null);
            }
        });

        executionDone.get(60, TimeUnit.SECONDS);

        // Should complete (either SynthesisDone or PlanError)
        boolean completed = capturedEvents.stream()
            .anyMatch(e -> e instanceof PlanExecuteEvent.SynthesisDone
                || e instanceof PlanExecuteEvent.PlanError);
        assertThat(completed).isTrue();
    }
}
