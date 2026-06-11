package com.shopai.agent.llm;

import com.shopai.agent.domain.ChatRequest;
import com.shopai.agent.domain.LlmResponse;

public interface LlmAdapter {
    LlmResponse chat(ChatRequest request);
}
