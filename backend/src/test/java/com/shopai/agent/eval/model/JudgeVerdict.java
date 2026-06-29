package com.shopai.agent.eval.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Scoring verdict from the LLM judge for a single agent answer.
 * All scores are on a 1-5 scale.
 */
public record JudgeVerdict(
    @JsonProperty("factualAccuracy")
    int factualAccuracy,      // 1-5: fact correctness (numbers, dates, names, statuses)

    @JsonProperty("completeness")
    int completeness,          // 1-5: coverage of key information from reference

    @JsonProperty("conciseness")
    int conciseness,           // 1-5: brevity, no fluff

    @JsonProperty("hallucination")
    int hallucination,         // 5 = no hallucination, 1 = severe fabrication

    @JsonProperty("overall")
    int overall,               // 1-5: holistic score

    @JsonProperty("comment")
    String comment             // Judge's explanation (Chinese or English)
) {
    /**
     * Returns the average of the four dimension scores (not including overall).
     */
    public double averageDimensionScore() {
        return (factualAccuracy + completeness + conciseness + hallucination) / 4.0;
    }
}
