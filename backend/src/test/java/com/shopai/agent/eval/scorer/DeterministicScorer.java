package com.shopai.agent.eval.scorer;

import com.shopai.agent.eval.runner.AgentExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Computes deterministic (non-LLM) scores for keyword presence/absence
 * and tool selection correctness.
 * <p>
 * These scores complement the LLM-as-Judge evaluation by providing
 * objective, rule-based checks that don't rely on another LLM's judgment.
 */
public class DeterministicScorer {

    private static final Logger log = LoggerFactory.getLogger(DeterministicScorer.class);

    /**
     * Scores keyword presence in the agent's answer.
     *
     * @param answer           the agent's text response
     * @param expectedKeywords keywords that SHOULD appear
     * @return fraction of expected keywords found (0.0–1.0);
     *         returns 1.0 if the expected list is empty (nothing to check)
     */
    public double scoreKeywordRecall(String answer, List<String> expectedKeywords) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 1.0;
        }
        String text = answer != null ? answer : "";
        long matched = expectedKeywords.stream()
            .filter(text::contains)
            .count();
        double recall = (double) matched / expectedKeywords.size();
        log.debug("Keyword recall: {}/{} = {:.2f}", matched, expectedKeywords.size(), recall);
        return recall;
    }

    /**
     * Scores absence of forbidden keywords in the agent's answer.
     *
     * @param answer            the agent's text response
     * @param forbiddenKeywords keywords that MUST NOT appear
     * @return 1.0 if no forbidden keywords are found, 0.0 if any are found;
     *         returns 1.0 if the forbidden list is empty (nothing to check)
     */
    public double scoreKeywordPrecision(String answer, List<String> forbiddenKeywords) {
        if (forbiddenKeywords == null || forbiddenKeywords.isEmpty()) {
            return 1.0;
        }
        String text = answer != null ? answer : "";
        List<String> found = forbiddenKeywords.stream()
            .filter(text::contains)
            .toList();
        if (!found.isEmpty()) {
            log.debug("Keyword precision: 0.0 — forbidden keywords found: {}", found);
            return 0.0;
        }
        return 1.0;
    }

    /**
     * Scores whether the agent selected the expected tool.
     *
     * @param toolCalls    the tool calls the agent actually made
     * @param expectedTool the tool name that should have been called (null if no tool expected)
     * @return 1.0 if the expected tool was called (or if no tool was expected),
     *         0.0 if the expected tool was not called
     */
    public double scoreToolSelection(List<AgentExecution.ToolCall> toolCalls,
                                      String expectedTool) {
        if (expectedTool == null) {
            // No specific tool expected — skip check
            return 1.0;
        }
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.debug("Tool selection: 0.0 — expected '{}' but no tools were called", expectedTool);
            return 0.0;
        }
        boolean found = toolCalls.stream()
            .anyMatch(tc -> expectedTool.equals(tc.toolName()));
        double score = found ? 1.0 : 0.0;
        if (!found) {
            List<String> actualNames = toolCalls.stream()
                .map(AgentExecution.ToolCall::toolName)
                .toList();
            log.debug("Tool selection: 0.0 — expected '{}', actual: {}", expectedTool, actualNames);
        }
        return score;
    }

    /**
     * Scores how well the agent's tool arguments match the expected arguments.
     * <p>
     * Performs a subset match: every key in {@code expectedArgs} must be present
     * in at least one actual tool call with a matching value.
     *
     * @param toolCalls    the tool calls the agent actually made
     * @param expectedTool the expected tool name (used to filter relevant calls)
     * @param expectedArgs the expected argument entries (null or empty → skip check, returns 1.0)
     * @return fraction of expected arg entries that matched (0.0–1.0)
     */
    public double scoreToolArgs(List<AgentExecution.ToolCall> toolCalls,
                                 String expectedTool, Map<String, Object> expectedArgs) {
        if (expectedArgs == null || expectedArgs.isEmpty()) {
            return 1.0;
        }
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.debug("Tool arg match: 0.0 — no tools were called");
            return 0.0;
        }

        // Find the tool call matching the expected tool (prefer exact match, fallback to any)
        AgentExecution.ToolCall matchedCall = null;
        if (expectedTool != null) {
            matchedCall = toolCalls.stream()
                .filter(tc -> expectedTool.equals(tc.toolName()))
                .findFirst()
                .orElse(null);
        }
        if (matchedCall == null) {
            // Fallback: take the first tool call with any args
            matchedCall = toolCalls.stream()
                .filter(tc -> tc.args() != null && !tc.args().isEmpty())
                .findFirst()
                .orElse(null);
        }
        if (matchedCall == null || matchedCall.args() == null || matchedCall.args().isEmpty()) {
            log.debug("Tool arg match: 0.0 — no tool call with args found");
            return 0.0;
        }

        Map<String, Object> actualArgs = matchedCall.args();
        int total = expectedArgs.size();
        int matched = 0;
        for (var entry : expectedArgs.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = actualArgs.get(key);
            if (actualValue != null && valuesMatch(expectedValue, actualValue)) {
                matched++;
            } else {
                log.debug("Tool arg mismatch for '{}': expected={}, actual={}", key, expectedValue, actualValue);
            }
        }

        double score = (double) matched / total;
        log.debug("Tool arg match: {}/{} = {:.2f}", matched, total, score);
        return score;
    }

    /**
     * Compares two argument values for equality, handling type coercion.
     * Both sides are converted to strings for comparison so that
     * {@code "20240611001"} (String) matches {@code 20240611001} (Number).
     */
    private boolean valuesMatch(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return false;
        }
        // Direct equality
        if (expected.equals(actual)) {
            return true;
        }
        // String-based fuzzy match (handles Number vs String, etc.)
        return String.valueOf(expected).equals(String.valueOf(actual));
    }
}
