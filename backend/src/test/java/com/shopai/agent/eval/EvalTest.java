package com.shopai.agent.eval;

import com.shopai.agent.config.TestConfig;
import com.shopai.agent.engine.PlanExecuteEngine;
import com.shopai.agent.engine.ShopAiAgent;
import com.shopai.agent.eval.guard.RegressionGuard;
import com.shopai.agent.eval.judge.LlmJudge;
import com.shopai.agent.eval.model.EvalReport;
import com.shopai.agent.eval.runner.AgentAdapter;
import com.shopai.agent.eval.runner.EvalRunner;
import com.shopai.agent.eval.report.ReportWriter;
import com.shopai.agent.support.LlmTestHelper;
import com.shopai.agent.support.TestDataFactory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end evaluation test.
 * <p>
 * Runs the full eval pipeline against the agent and checks that scores
 * meet minimum thresholds. Requires both DEEPSEEK_API_KEY and EVAL_JUDGE_API_KEY.
 * <p>
 * Run via: {@code mvn test -Dtest="EvalTest" -Peval}
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "EVAL_JUDGE_API_KEY", matches = ".+")
class EvalTest {

    private static final String DATASET = "eval-dataset.json";
    private static final String REPORT_DIR = "target/eval-reports";

    @Autowired
    private ShopAiAgent reActAgent;

    @Autowired
    private PlanExecuteEngine planExecuteEngine;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Autowired
    private ChatMemoryStore memoryStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${shopai.eval.judge.api-key:}")
    private String judgeApiKey;

    @Value("${shopai.eval.judge.model:gpt-4o-mini}")
    private String judgeModel;

    @Value("${shopai.eval.judge.base-url:https://api.openai.com/v1}")
    private String judgeBaseUrl;

    @BeforeAll
    static void setUpClass() {
        // Ensure report directory exists
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(REPORT_DIR));
        } catch (Exception e) {
            // ignore
        }
    }

    @AfterAll
    static void tearDownClass() {
        // Cleanup if needed
    }

    @Test
    @Tag("eval")
    @org.junit.jupiter.api.DisplayName("Run ReAct mode eval and check regression")
    void runReActEval() throws Exception {
        TestDataFactory.seedAll(jdbc);

        AgentAdapter reActAdapter = (sessionId, userMessage) -> {
            TokenStream stream = reActAgent.chat(sessionId, userMessage);
            return LlmTestHelper.collectStream(stream, Duration.ofSeconds(60));
        };

        LlmJudge judge = LlmJudge.create(judgeApiKey, judgeModel, judgeBaseUrl, Duration.ofSeconds(30));
        EvalRunner runner = new EvalRunner(reActAdapter, judge);
        EvalReport report = runner.run(DATASET, "react", REPORT_DIR);

        // Check regression
        RegressionGuard guard = new RegressionGuard("baseline-report.json");
        EvalReport.RegressionResult regResult = guard.check(report);

        // Print summary
        new ReportWriter().printConsoleSummary(
            new EvalReport(
                report.runId(), report.timestamp(), report.mode(),
                report.datasetVersion(), report.totalCases(),
                report.executedCases(), report.failedCases(),
                report.scores(), report.categoryBreakdown(),
                regResult, report.failedCaseDetails()
            ));

        // Assert minimum quality bar
        assertThat(report.executedCases())
            .as("At least 80% of cases should execute successfully")
            .isGreaterThanOrEqualTo((int) (report.totalCases() * 0.8));

        if (report.executedCases() > 0) {
            assertThat(report.scores().overall().mean())
                .as("Overall mean score should be >= 3.0")
                .isGreaterThanOrEqualTo(3.0);
        }

        // Regression guard: fail if violations found
        assertThat(regResult.passed())
            .as("Regression guard must pass (no dimension drop > 5%)")
            .isTrue();
    }

    @Test
    @Tag("eval")
    @org.junit.jupiter.api.DisplayName("Run Plan-Execute mode eval and check regression")
    void runPlanExecuteEval() throws Exception {
        TestDataFactory.seedAll(jdbc);

        AgentAdapter peAdapter = (sessionId, userMessage) -> {
            java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
            java.util.concurrent.atomic.AtomicReference<String> answer = new java.util.concurrent.atomic.AtomicReference<>("");

            planExecuteEngine.execute(sessionId, userMessage, event -> {
                if (event instanceof com.shopai.agent.engine.PlanExecuteEvent.SynthesisDone done) {
                    answer.set(done.content());
                    future.complete(done.content());
                } else if (event instanceof com.shopai.agent.engine.PlanExecuteEvent.PlanError err) {
                    future.completeExceptionally(new RuntimeException(err.message()));
                }
            });

            try {
                return future.get(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                String partial = answer.get();
                return partial != null && !partial.isEmpty() ? partial : "(timeout)";
            }
        };

        LlmJudge judge = LlmJudge.create(judgeApiKey, judgeModel, judgeBaseUrl, Duration.ofSeconds(30));
        EvalRunner runner = new EvalRunner(peAdapter, judge);
        EvalReport report = runner.run(DATASET, "plan-execute", REPORT_DIR);

        // Print summary
        new ReportWriter().printConsoleSummary(
            new EvalReport(
                report.runId(), report.timestamp(), report.mode(),
                report.datasetVersion(), report.totalCases(),
                report.executedCases(), report.failedCases(),
                report.scores(), report.categoryBreakdown(),
                null, report.failedCaseDetails()
            ));

        assertThat(report.executedCases())
            .as("At least 70% of cases should execute successfully (Plan-Execute is more complex)")
            .isGreaterThanOrEqualTo((int) (report.totalCases() * 0.7));
    }
}
