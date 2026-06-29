package com.shopai.agent.eval.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Aggregated evaluation report for a complete eval run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvalReport(
    String runId,
    String timestamp,
    String mode,
    String datasetVersion,
    int totalCases,
    int executedCases,
    int failedCases,
    DimensionScores scores,
    Map<String, CategoryBreakdown> categoryBreakdown,
    RegressionResult regressionGuard,
    List<FailedCase> failedCaseDetails
) {
    /** Per-dimension statistics. */
    public record DimensionScores(
        Stats factualAccuracy,
        Stats completeness,
        Stats conciseness,
        Stats hallucination,
        Stats overall
    ) {}

    /** Statistical summary for a single dimension. */
    public record Stats(
        double mean,
        double median,
        int min,
        int max,
        double p95
    ) {}

    /** Category-level breakdown. */
    public record CategoryBreakdown(
        int count,
        DimensionScores scores
    ) {}

    /** Regression guard comparison result. */
    public record RegressionResult(
        boolean checked,
        String baselineRunId,
        boolean passed,
        List<Violation> violations
    ) {}

    public record Violation(
        String dimension,
        double baselineMean,
        double currentMean,
        double dropPct
    ) {}

    public record FailedCase(
        String id,
        String error
    ) {}
}
