package com.shopai.agent.eval.scorer;

import com.shopai.agent.eval.model.EvalReport;
import com.shopai.agent.eval.model.EvalResult;
import com.shopai.agent.eval.model.JudgeVerdict;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates individual {@link EvalResult} instances into an {@link EvalReport}
 * with per-dimension statistics and per-category breakdowns.
 * <p>
 * Covers both LLM-judge dimensions and deterministic (keyword + tool) dimensions.
 */
public class EvalScorer {

    /**
     * Aggregates eval results into a report.
     */
    public EvalReport aggregate(String runId, String mode, String datasetVersion, List<EvalResult> results) {
        List<EvalResult> successful = results.stream()
            .filter(r -> r.judgeVerdict() != null)
            .toList();
        List<EvalResult> failed = results.stream()
            .filter(r -> r.judgeVerdict() == null)
            .toList();

        // Per-dimension stats — LLM judge dimensions
        EvalReport.DimensionScores scores = new EvalReport.DimensionScores(
            computeStats(successful, r -> (double) r.judgeVerdict().factualAccuracy()),
            computeStats(successful, r -> (double) r.judgeVerdict().completeness()),
            computeStats(successful, r -> (double) r.judgeVerdict().conciseness()),
            computeStats(successful, r -> (double) r.judgeVerdict().hallucination()),
            computeStats(successful, r -> (double) r.judgeVerdict().overall()),
            // Deterministic dimensions
            computeStats(successful, EvalResult::keywordRecall),
            computeStats(successful, EvalResult::keywordPrecision),
            computeStats(successful, EvalResult::toolSelectionMatch),
            computeStats(successful, EvalResult::toolArgMatch)
        );

        // Per-category breakdown
        Map<String, List<EvalResult>> byCategory = successful.stream()
            .collect(Collectors.groupingBy(EvalResult::category));

        Map<String, EvalReport.CategoryBreakdown> breakdown = new LinkedHashMap<>();
        for (var entry : byCategory.entrySet()) {
            List<EvalResult> catResults = entry.getValue();
            breakdown.put(entry.getKey(), new EvalReport.CategoryBreakdown(
                catResults.size(),
                new EvalReport.DimensionScores(
                    computeStats(catResults, r -> (double) r.judgeVerdict().factualAccuracy()),
                    computeStats(catResults, r -> (double) r.judgeVerdict().completeness()),
                    computeStats(catResults, r -> (double) r.judgeVerdict().conciseness()),
                    computeStats(catResults, r -> (double) r.judgeVerdict().hallucination()),
                    computeStats(catResults, r -> (double) r.judgeVerdict().overall()),
                    computeStats(catResults, EvalResult::keywordRecall),
                    computeStats(catResults, EvalResult::keywordPrecision),
                    computeStats(catResults, EvalResult::toolSelectionMatch),
                    computeStats(catResults, EvalResult::toolArgMatch)
                )
            ));
        }

        List<EvalReport.FailedCase> failedDetails = failed.stream()
            .map(r -> new EvalReport.FailedCase(r.caseId(), r.error()))
            .toList();

        return new EvalReport(
            runId,
            java.time.Instant.now().toString(),
            mode,
            datasetVersion,
            results.size(),
            successful.size(),
            failed.size(),
            scores,
            breakdown,
            null,  // regression guard set separately
            failedDetails.isEmpty() ? null : failedDetails
        );
    }

    @FunctionalInterface
    private interface ScoreExtractor {
        double extract(EvalResult r);
    }

    private EvalReport.Stats computeStats(List<EvalResult> results, ScoreExtractor extractor) {
        if (results.isEmpty()) {
            return new EvalReport.Stats(0, 0, 0, 0, 0);
        }

        List<Double> scores = results.stream()
            .map(extractor::extract)
            .sorted()
            .toList();

        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double median = scores.get(scores.size() / 2);
        int min = (int) Math.floor(scores.get(0));
        int max = (int) Math.ceil(scores.get(scores.size() - 1));

        // P95
        int p95Index = (int) Math.ceil(0.95 * scores.size()) - 1;
        p95Index = Math.max(0, Math.min(p95Index, scores.size() - 1));
        double p95 = scores.get(p95Index);

        return new EvalReport.Stats(
            Math.round(mean * 100.0) / 100.0,
            median,
            min,
            max,
            p95
        );
    }
}
