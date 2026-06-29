package com.shopai.agent.eval.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.eval.model.JudgeVerdict;
import com.shopai.agent.llm.LangChain4jAdapter;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * LLM-as-Judge: uses a separate LLM to score agent answers against reference answers.
 * <p>
 * The judge model should ideally be a different (preferably stronger) model than
 * the agent being evaluated, to reduce self-judging bias.
 */
public class LlmJudge {

    private static final Logger log = LoggerFactory.getLogger(LlmJudge.class);

    private final ChatModel judgeModel;
    private final ObjectMapper mapper;

    /**
     * Creates a judge backed by the given ChatModel.
     */
    public LlmJudge(ChatModel judgeModel) {
        this.judgeModel = judgeModel;
        this.mapper = new ObjectMapper();
    }

    /**
     * Factory method using separate API configuration.
     */
    public static LlmJudge create(String apiKey, String model, String baseUrl, Duration timeout) {
        ChatModel chatModel = LangChain4jAdapter.createChatModel(apiKey, model, baseUrl, timeout, 0.0);
        return new LlmJudge(chatModel);
    }

    /**
     * Evaluates a single agent answer and returns scores.
     *
     * @param question        the user's original question
     * @param agentAnswer     the agent's response
     * @param referenceAnswer the human-annotated reference
     * @return JudgeVerdict with dimension scores
     * @throws Exception if the judge call fails or JSON cannot be parsed
     */
    public JudgeVerdict evaluate(String question, String agentAnswer, String referenceAnswer) throws Exception {
        String userMessage = JudgePrompts.buildJudgeUserMessage(question, agentAnswer, referenceAnswer);

        // First attempt
        String rawResponse = judgeModel.chat(
            JudgePrompts.JUDGE_SYSTEM_PROMPT + "\n\n" + userMessage);

        try {
            return parseVerdict(rawResponse);
        } catch (Exception e) {
            log.warn("Judge JSON parse failed on first attempt: {}", e.getMessage());
            // Retry with stricter prompt
            String retryPrompt = userMessage + "\n\nIMPORTANT: Output ONLY the JSON object. No markdown, no explanation.";
            String retryResponse = judgeModel.chat(
                JudgePrompts.JUDGE_SYSTEM_PROMPT + "\n\n" + retryPrompt);

            try {
                return parseVerdict(retryResponse);
            } catch (Exception e2) {
                log.error("Judge JSON parse failed on retry: {}", e2.getMessage());
                throw new RuntimeException("Failed to parse judge response after retry: " + e2.getMessage(), e2);
            }
        }
    }

    /**
     * Parses the LLM response text into a JudgeVerdict.
     * Strips markdown code fences if present.
     */
    JudgeVerdict parseVerdict(String raw) throws Exception {
        String cleaned = raw.trim();

        // Strip ```json ... ``` fences
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-z]*\n?", "").replaceAll("\n```", "").trim();
        }

        // Find the first { and last } to extract JSON
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        return mapper.readValue(cleaned, JudgeVerdict.class);
    }
}
