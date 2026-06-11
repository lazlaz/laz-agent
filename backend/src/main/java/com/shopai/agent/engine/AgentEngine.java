package com.shopai.agent.engine;

import com.shopai.agent.domain.AgentRequest;
import com.shopai.agent.domain.AgentResponse;

public interface AgentEngine {
    AgentResponse execute(AgentRequest request);
}
