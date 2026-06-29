package com.shopai.agent.eval.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.eval.model.EvalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares the current eval report against a baseline to detect regressions.
 * <p>
 * The baseline is a checked-in JSON file ({@code baseline-report.json})
 * that represents the "known good" scores. If any dimension drops by more
 * than the configured threshold, the guard flags a violation.
 */
public class RegressionGuard {

    private static final Logger log = LoggerFactory.getLogger(RegressionGuard.class);

    private static final double DEFAULT_THRESHOLD_PCT = 5.0;
    private static final String[] MONITORED_DIMENSIONS = {
        "factualAccuracy", "completeness", "hallucination", "overall"
    };

    private final String baselineResource;
    private final double thresholdPct;
    private final ObjectMapper mapper;

    public RegressionGuard(String baselineResource, double thresholdPct) {
        this.baselineResource = baselineResource;
        this.thresholdPct = thresholdPct;
        this.mapper = new ObjectMapper();
    }

    public RegressionGuard(String baselineResource) {
        this(baselineResource, DEFAULT_THRESHOLD_PCT);
    }

    /**
     * Checks the current report against the baseline.
     *
     * @param current the current eval report
     * @return the regression result (set on the report by the caller)
     */
    public EvalReport.RegressionResult check(EvalReport current) {
        // Try to load baseline
        EvalReport baseline;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(baselineResource)) {
            if (in == null) {
                log.warn("Baseline report not found at '{}'. Skipping regression check. "
                       + "Run a full eval to generate one.", baselineResource);
                return new EvalReport.RegressionResult(true, null, true, List.of());
            }
            baseline = mapper.readValue(in, EvalReport.class);
        } catch (IOException e) {
            log.warn("Failed to load baseline report: {}. Skipping regression check.", e.getMessage());
            return new EvalReport.RegressionResult(true, null, true, List.of());
        }

        List<EvalReport.Violation> violations = new ArrayList<>();

        // Compare each monitored dimension
        var baselineScores = baseline.scores();
        var currentScores = current.scores();

        checkDimension("Factual Accuracy", baselineScores.factualAccuracy(),
            currentScores.factualAccuracy(), violations);
        checkDimension("Completeness", baselineScores.completeness(),
            currentScores.completeness(), violations);
        checkDimension("Hallucination", baselineScores.hallucination(),
            currentScores.hallucination(), violations);
        checkDimension("OVERALL", baselineScores.overall(),
            currentScores.overall(), violations);

        boolean passed = violations.isEmpty();
        if (passed) {
            log.info("Regression guard: PASS (no violations > {}%)", thresholdPct);
        } else {
            log.warn("Regression guard: FAIL — {} violations found", violations.size());
            for (var v : violations) {
                log.warn("  {}: {:.2f} → {:.2f} ({:.1f}% drop)",
                    v.dimension(), v.baselineMean(), v.currentMean(), v.dropPct());
            }
        }

        return new EvalReport.RegressionResult(true, baseline.runId(), passed, violations);
    }

    private void checkDimension(String name, EvalReport.Stats baselineStats,
                                 EvalReport.Stats currentStats,
                                 List<EvalReport.Violation> violations) {
        double baselineMean = baselineStats.mean();
        double currentMean = currentStats.mean();

        if (baselineMean <= 0) return; // Can't compare against zero baseline

        double dropPct = (baselineMean - currentMean) / baselineMean * 100;
        if (dropPct > thresholdPct) {
            violations.add(new EvalReport.Violation(name, baselineMean, currentMean, dropPct));
        }
    }
}
