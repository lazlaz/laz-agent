package com.shopai.agent.domain;

import java.util.List;

public record AgentResponse(
    String content,
    List<StepRecord> steps,
    TokenUsage totalUsage,
    long latencyMs
) {}
