package com.shopai.agent.eval.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * A single evaluation test case with expected behavior annotations.
 */
public record EvalCase(
    String id,
    String category,          // tool_selection | rag_retrieval | calculation | multi_turn
    String subcategory,       // e.g., order_query, return_policy
    String difficulty,        // easy | medium | hard
    String userMessage,       // The input to send to the agent
    String mode,              // react | plan-execute | both
    String expectedTool,      // null if no tool expected
    Map<String, Object> expectedArgs,   // null if not applicable
    List<String> expectedKeywords,      // Keywords the answer SHOULD contain
    List<String> forbiddenKeywords,     // Keywords the answer should NOT contain
    String referenceAnswer,  // Human-annotated gold answer for LLM-as-Judge
    double minScore          // Minimum acceptable overall score (1-5)
) {
    public EvalCase {
        if (expectedKeywords == null) expectedKeywords = List.of();
        if (forbiddenKeywords == null) forbiddenKeywords = List.of();
        if (expectedArgs == null) expectedArgs = Map.of();
    }
}
