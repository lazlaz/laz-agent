package com.shopai.agent.eval.scorer;

import com.shopai.agent.eval.model.EvalReport;
import com.shopai.agent.eval.model.EvalResult;
import com.shopai.agent.eval.model.JudgeVerdict;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates individual {@link EvalResult} instances into an {@link EvalReport}
 * with per-dimension statistics and per-category breakdowns.
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

        // Per-dimension stats
        EvalReport.DimensionScores scores = new EvalReport.DimensionScores(
            computeStats(successful, v -> v.factualAccuracy()),
            computeStats(successful, v -> v.completeness()),
            computeStats(successful, v -> v.conciseness()),
            computeStats(successful, v -> v.hallucination()),
            computeStats(successful, v -> v.overall())
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
                    computeStats(catResults, v -> v.factualAccuracy()),
                    computeStats(catResults, v -> v.completeness()),
                    computeStats(catResults, v -> v.conciseness()),
                    computeStats(catResults, v -> v.hallucination()),
                    computeStats(catResults, v -> v.overall())
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
        int extract(JudgeVerdict v);
    }

    private EvalReport.Stats computeStats(List<EvalResult> results, ScoreExtractor extractor) {
        if (results.isEmpty()) {
            return new EvalReport.Stats(0, 0, 0, 0, 0);
        }

        List<Integer> scores = results.stream()
            .map(r -> extractor.extract(r.judgeVerdict()))
            .sorted()
            .toList();

        double mean = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        double median = scores.get(scores.size() / 2);
        int min = scores.get(0);
        int max = scores.get(scores.size() - 1);

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
