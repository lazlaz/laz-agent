package com.shopai.agent.eval.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.eval.judge.LlmJudge;
import com.shopai.agent.eval.model.EvalCase;
import com.shopai.agent.eval.model.EvalReport;
import com.shopai.agent.eval.model.EvalResult;
import com.shopai.agent.eval.model.JudgeVerdict;
import com.shopai.agent.eval.report.ReportWriter;
import com.shopai.agent.eval.scorer.DeterministicScorer;
import com.shopai.agent.eval.scorer.EvalScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main evaluation orchestrator.
 * <p>
 * Loads a dataset, runs each case through the agent, judges the answers
 * with both LLM-based and deterministic scoring, aggregates scores,
 * and writes a report.
 */
public class EvalRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final AgentAdapter agentAdapter;
    private final LlmJudge judge;
    private final DeterministicScorer deterministicScorer;
    private final EvalScorer scorer;
    private final ReportWriter writer;
    private final ObjectMapper mapper;

    public EvalRunner(AgentAdapter agentAdapter, LlmJudge judge) {
        this.agentAdapter = agentAdapter;
        this.judge = judge;
        this.deterministicScorer = new DeterministicScorer();
        this.scorer = new EvalScorer();
        this.writer = new ReportWriter();
        this.mapper = new ObjectMapper();
    }

    /**
     * Runs the full eval pipeline.
     *
     * @param datasetResource classpath resource path to eval-dataset.json
     * @param mode            "react" or "plan-execute"
     * @param reportDir       directory to write the report JSON (e.g., "target/eval-reports")
     * @return the eval report
     */
    public EvalReport run(String datasetResource, String mode, String reportDir) throws Exception {
        String runId = "eval-" + java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        // Load dataset
        log.info("Loading eval dataset from: {}", datasetResource);
        EvalCase[] cases;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(datasetResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Dataset not found: " + datasetResource);
            }
            cases = mapper.readValue(in, EvalCase[].class);
        }
        log.info("Loaded {} eval cases", cases.length);

        // Filter by mode
        List<EvalCase> applicableCases = new ArrayList<>();
        for (EvalCase c : cases) {
            if ("both".equals(c.mode()) || mode.equals(c.mode())) {
                applicableCases.add(c);
            }
        }
        log.info("Applicable cases for mode '{}': {}/{}", mode, applicableCases.size(), cases.length);

        // Execute each case
        List<EvalResult> results = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);

        for (EvalCase c : applicableCases) {
            String sessionId = runId + "-" + c.id();
            log.info("[{}/{}] Running: {} — \"{}\"",
                completed.get() + 1, applicableCases.size(), c.id(), c.userMessage());

            try {
                long start = System.currentTimeMillis();
                AgentExecution execution = agentAdapter.execute(sessionId, c.userMessage());
                long latency = System.currentTimeMillis() - start;
                String agentAnswer = execution.answer();

                // ── Deterministic scoring ──────────────────────────
                double keywordRecall = deterministicScorer.scoreKeywordRecall(
                    agentAnswer, c.expectedKeywords());
                double keywordPrecision = deterministicScorer.scoreKeywordPrecision(
                    agentAnswer, c.forbiddenKeywords());
                double toolSelectionMatch = deterministicScorer.scoreToolSelection(
                    execution.toolCalls(), c.expectedTool());
                double toolArgMatch = deterministicScorer.scoreToolArgs(
                    execution.toolCalls(), c.expectedTool(), c.expectedArgs());

                // ── LLM judge ─────────────────────────────────────
                JudgeVerdict verdict = judge.evaluate(
                    c.userMessage(), agentAnswer, c.referenceAnswer());

                results.add(EvalResult.success(c, mode, agentAnswer, verdict, latency,
                    keywordRecall, keywordPrecision, toolSelectionMatch, toolArgMatch));

                log.info("  Agent answer: {}", agentAnswer);
                log.info("  Reference answer: {}", c.referenceAnswer());
                log.info("  ✓ LLM scores: factual={}, complete={}, concise={}, halluc={}, overall={}",
                    verdict.factualAccuracy(), verdict.completeness(),
                    verdict.conciseness(), verdict.hallucination(),
                    verdict.overall());
                log.info("  ✓ Deterministic: kwRecall={:.2f}, kwPrecision={:.2f}, toolSelect={:.2f}, toolArgs={:.2f} ({}ms)",
                    keywordRecall, keywordPrecision, toolSelectionMatch, toolArgMatch, latency);

            } catch (Exception e) {
                log.error("  ✗ Failed: {}", e.getMessage());
                results.add(EvalResult.failed(c, mode, e.getMessage()));
            }

            completed.incrementAndGet();
        }

        // Aggregate and report
        EvalReport report = scorer.aggregate(runId, mode, "1.0", results);
        writer.write(report, reportDir);

        return report;
    }
}
