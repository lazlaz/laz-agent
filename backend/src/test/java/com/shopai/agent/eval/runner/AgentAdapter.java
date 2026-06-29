package com.shopai.agent.eval.runner;

/**
 * Abstraction over the two agent execution modes (ReAct and Plan-Execute).
 * Decouples the eval runner from engine-specific APIs.
 */
@FunctionalInterface
public interface AgentAdapter {

    /**
     * Executes a user message against the agent and returns the complete answer.
     *
     * @param sessionId   unique session ID for this conversation turn
     * @param userMessage the user's input
     * @return the agent's complete text response
     * @throws Exception if agent execution fails
     */
    String execute(String sessionId, String userMessage) throws Exception;
}
