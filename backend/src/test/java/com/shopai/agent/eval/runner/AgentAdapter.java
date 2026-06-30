package com.shopai.agent.eval.runner;

/**
 * Abstraction over the two agent execution modes (ReAct and Plan-Execute).
 * Decouples the eval runner from engine-specific APIs.
 */
@FunctionalInterface
public interface AgentAdapter {

    /**
     * Executes a user message against the agent and returns the complete answer
     * along with any tool calls the agent made.
     *
     * @param sessionId   unique session ID for this conversation turn
     * @param userMessage the user's input
     * @return the agent's execution result (text answer + tool calls)
     * @throws Exception if agent execution fails
     */
    AgentExecution execute(String sessionId, String userMessage) throws Exception;
}
