package com.shopai.agent.eval.model;

/**
 * The result of running a single eval case: agent answer + judge scores + deterministic scores.
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
    String error,                    // null if no error

    // ── Deterministic scores (not LLM-based) ─────────────────────────

    /** Fraction of expectedKeywords found in the answer (0.0–1.0). */
    double keywordRecall,

    /** 1.0 if no forbidden keywords found, 0.0 otherwise. */
    double keywordPrecision,

    /** 1.0 if the expected tool was called, 0.0 otherwise. */
    double toolSelectionMatch,

    /** Fraction of expectedArgs entries that matched the actual tool arguments (0.0–1.0). */
    double toolArgMatch
) {
    /** Create a result for a case that failed to execute. */
    public static EvalResult failed(EvalCase c, String mode, String error) {
        return new EvalResult(
            c.id(), c.category(), c.subcategory(), c.difficulty(),
            mode, c.userMessage(), "", c.referenceAnswer(),
            null, 0, error,
            0.0, 0.0, 0.0, 0.0);
    }

    /** Create a result for a successfully judged case with deterministic scores. */
    public static EvalResult success(EvalCase c, String mode,
                                      String agentAnswer, JudgeVerdict verdict, long latencyMs,
                                      double keywordRecall, double keywordPrecision,
                                      double toolSelectionMatch, double toolArgMatch) {
        return new EvalResult(
            c.id(), c.category(), c.subcategory(), c.difficulty(),
            mode, c.userMessage(), agentAnswer, c.referenceAnswer(),
            verdict, latencyMs, null,
            keywordRecall, keywordPrecision, toolSelectionMatch, toolArgMatch);
    }

    /**
     * Legacy factory for callers that haven't been updated for deterministic scoring.
     * All deterministic scores default to 1.0 (pass).
     */
    public static EvalResult success(EvalCase c, String mode,
                                      String agentAnswer, JudgeVerdict verdict, long latencyMs) {
        return success(c, mode, agentAnswer, verdict, latencyMs, 1.0, 1.0, 1.0, 1.0);
    }
}
