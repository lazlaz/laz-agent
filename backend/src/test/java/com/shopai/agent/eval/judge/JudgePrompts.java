package com.shopai.agent.eval.judge;

/**
 * Prompt templates for the LLM-as-Judge evaluation system.
 */
public final class JudgePrompts {

    private JudgePrompts() {}

    /**
     * System prompt that instructs the judge LLM how to score agent answers.
     */
    public static final String JUDGE_SYSTEM_PROMPT = """
        You are an impartial evaluator assessing the quality of an AI customer service agent's answers.

        You will receive:
        1. User Question — what the customer asked
        2. AI Agent Answer — what the AI agent responded
        3. Reference Answer — the human-annotated ideal answer

        Score the AI Agent Answer on these 4 dimensions (1-5 scale):

        A. Factual Accuracy (factualAccuracy): Are numbers, dates, names, statuses, and facts correct?
           5 = Fully accurate, all facts match reference
           3 = Some minor errors or omissions
           1 = Major factual errors

        B. Completeness (completeness): Does the answer cover all key information from the reference?
           5 = Fully covers all key points
           3 = Covers most but misses some details
           1 = Missing critical information

        C. Conciseness (conciseness): Is the answer clear and to the point, without unnecessary fluff?
           5 = Very concise and clear
           3 = Somewhat verbose or too brief
           1 = Excessively long or too terse to be useful

        D. Hallucination (hallucination): Does the answer fabricate information not present in the reference?
           5 = No hallucination, all information is grounded
           3 = Minor harmless embellishment
           1 = Severe fabrication of false information

        E. Overall (overall): Your holistic assessment (1-5).

        Output ONLY valid JSON (no markdown fences, no explanation outside JSON):
        {"factualAccuracy":5,"completeness":4,"conciseness":5,"hallucination":5,"overall":5,"comment":"Brief scoring rationale in one sentence"}
        """;

    /**
     * Builds the user message containing the case details for the judge.
     */
    public static String buildJudgeUserMessage(String userQuestion, String agentAnswer, String referenceAnswer) {
        return String.format("""
            ## User Question
            %s

            ## AI Agent Answer
            %s

            ## Reference Answer
            %s

            Output your scores as JSON:""",
            userQuestion,
            agentAnswer != null && !agentAnswer.isBlank() ? agentAnswer : "(empty response)",
            referenceAnswer);
    }
}
