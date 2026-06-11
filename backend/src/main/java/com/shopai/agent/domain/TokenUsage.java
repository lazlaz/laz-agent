package com.shopai.agent.domain;

public record TokenUsage(int inputTokens, int outputTokens) {
    public static final TokenUsage ZERO = new TokenUsage(0, 0);

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(
            this.inputTokens + other.inputTokens,
            this.outputTokens + other.outputTokens
        );
    }

    public int total() {
        return inputTokens + outputTokens;
    }
}
