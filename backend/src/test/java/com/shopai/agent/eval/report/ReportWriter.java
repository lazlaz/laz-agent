package com.shopai.agent.eval.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.shopai.agent.eval.model.EvalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes eval reports as JSON files and prints console summaries.
 */
public class ReportWriter {

    private static final Logger log = LoggerFactory.getLogger(ReportWriter.class);

    private final ObjectMapper mapper;

    public ReportWriter() {
        this.mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /**
     * Writes the report to a JSON file and prints a console summary.
     *
     * @param report    the eval report
     * @param reportDir directory path (e.g., "target/eval-reports")
     */
    public void write(EvalReport report, String reportDir) {
        // Console summary
        printConsoleSummary(report);

        // JSON file
        try {
            Path dir = Paths.get(reportDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(report.runId() + ".json");
            mapper.writeValue(file.toFile(), report);
            log.info("Report written to: {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write report: {}", e.getMessage());
        }
    }

    /**
     * Prints a formatted console summary of the eval report.
     */
    public void printConsoleSummary(EvalReport report) {
        String border = "═".repeat(45);

        System.out.println();
        System.out.println(border);
        System.out.printf("  ShopAI Agent Evaluation Report%n");
        System.out.printf("  Run: %s | Mode: %s%n", report.runId(), report.mode());
        System.out.printf("  Dataset: v%s | Cases: %d/%d (%d failed)%n",
            report.datasetVersion(), report.executedCases(),
            report.totalCases(), report.failedCases());
        System.out.println(border);

        EvalReport.DimensionScores s = report.scores();
        System.out.printf("%-18s %6s %6s %6s %6s %6s%n",
            "Dimension", "Mean", "Med", "Min", "Max", "P95");
        System.out.println("-".repeat(45));

        printDim("Factual Accuracy", s.factualAccuracy());
        printDim("Completeness", s.completeness());
        printDim("Conciseness", s.conciseness());
        printDim("Hallucination", s.hallucination());
        System.out.println("-".repeat(45));
        printDim("OVERALL", s.overall());
        System.out.println(border);

        // Regression guard
        if (report.regressionGuard() != null) {
            if (report.regressionGuard().passed()) {
                System.out.println("Regression Guard: ✅ PASS (no violations > 5%)");
            } else {
                System.out.println("Regression Guard: ❌ FAIL");
                for (var v : report.regressionGuard().violations()) {
                    System.out.printf("  - %s: %.2f → %.2f (%.1f%% drop)%n",
                        v.dimension(), v.baselineMean(), v.currentMean(), v.dropPct());
                }
            }
            System.out.println(border);
        }

        // Failed cases
        if (report.failedCaseDetails() != null && !report.failedCaseDetails().isEmpty()) {
            System.out.println("Failed cases:");
            for (var f : report.failedCaseDetails()) {
                System.out.printf("  - %s: %s%n", f.id(), f.error());
            }
            System.out.println(border);
        }

        System.out.println();
    }

    private void printDim(String name, EvalReport.Stats stats) {
        System.out.printf("%-18s %5.2f %5.1f %5d %5d %5.1f%n",
            name, stats.mean(), stats.median(),
            stats.min(), stats.max(), stats.p95());
    }
}
