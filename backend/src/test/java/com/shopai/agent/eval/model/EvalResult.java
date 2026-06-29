package com.shopai.agent.eval.model;

/**
 * The result of running a single eval case: agent answer + judge scores.
 */
public record EvalResult(
    String caseId,
    String category,
    String subcategory,
    String difficulty,
    String mode,
    String userMessage,
    String agentAnswer,
    String referenceAnswer,
    JudgeVerdict judgeVerdict,
    long latencyMs,
    String error               // null if no error
) {
    /** Create a result for a case that failed to execute. */
    public static EvalResult failed(EvalCase c, String mode, String error) {
        return new EvalResult(
            c.id(), c.category(), c.subcategory(), c.difficulty(),
            mode, c.userMessage(), "", c.referenceAnswer(),
            null, 0, error);
    }

    /** Create a result for a successfully judged case. */
    public static EvalResult success(EvalCase c, String mode,
                                      String agentAnswer, JudgeVerdict verdict, long latencyMs) {
        return new EvalResult(
            c.id(), c.category(), c.subcategory(), c.difficulty(),
            mode, c.userMessage(), agentAnswer, c.referenceAnswer(),
            verdict, latencyMs, null);
    }
}
