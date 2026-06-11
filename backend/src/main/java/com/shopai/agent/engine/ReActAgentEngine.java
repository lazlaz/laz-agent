package com.shopai.agent.engine;

import com.shopai.agent.domain.*;
import com.shopai.agent.llm.LlmAdapter;
import com.shopai.agent.memory.MemoryManager;
import com.shopai.agent.prompt.PromptEngine;
import com.shopai.agent.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ReActAgentEngine implements AgentEngine {

    private final LlmAdapter llm;
    private final PromptEngine prompt;
    private final MemoryManager memory;
    private final ToolRegistry tools;

    @Value("${shopai.agent.max-iterations:10}")
    private int maxIterations;

    @Value("${shopai.agent.max-history-messages:20}")
    private int maxHistoryMessages;

    public ReActAgentEngine(LlmAdapter llm, PromptEngine prompt, MemoryManager memory, ToolRegistry tools) {
        this.llm = llm;
        this.prompt = prompt;
        this.memory = memory;
        this.tools = tools;
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        List<StepRecord> steps = new ArrayList<>();
        TokenUsage totalUsage = TokenUsage.ZERO;

        memory.append(request.sessionId(), Message.of(Role.USER, request.userInput()));

        List<Message> history = memory.loadHistory(request.sessionId(), maxHistoryMessages);

        Map<String, Object> vars = Map.of(
            "tools", tools.listAll(),
            "history", history,
            "userInput", request.userInput()
        );

        int iteration = 0;
        String finalAnswer = null;

        while (iteration < maxIterations) {
            iteration++;

            ChatRequest chatRequest = prompt.build("react-system", vars);
            LlmResponse response = llm.chat(chatRequest);
            totalUsage = totalUsage.add(response.usage());

            switch (response.decision()) {
                case THOUGHT -> {
                    steps.add(StepRecord.thought(iteration, response.content()));
                    memory.append(request.sessionId(), Message.of(Role.ASSISTANT, response.content()));
                }
                case TOOL_CALL -> {
                    ToolCall call = response.toolCall();
                    steps.add(StepRecord.toolCall(iteration, call));
                    memory.append(request.sessionId(),
                        Message.of(Role.ASSISTANT, "Calling tool: " + call.name(),
                            Map.of("toolCall", call)));

                    ToolResult result = tools.execute(call);
                    steps.add(StepRecord.toolResult(iteration, result));
                    memory.append(request.sessionId(),
                        Message.of(Role.TOOL, result.content(),
                            Map.of("toolName", call.name(), "success", result.success())));

                    String toolResultMsg = String.format(
                        "Tool %s returned:\n%s",
                        call.name(), result.content()
                    );
                    List<Message> updatedHistory = new ArrayList<>(history);
                    updatedHistory.add(Message.of(Role.TOOL, toolResultMsg));
                    vars = Map.of(
                        "tools", tools.listAll(),
                        "history", updatedHistory,
                        "userInput", request.userInput()
                    );
                }
                case FINAL -> {
                    finalAnswer = response.content();
                    steps.add(StepRecord.finalAnswer(iteration, finalAnswer));
                    memory.append(request.sessionId(), Message.of(Role.ASSISTANT, finalAnswer));
                    break;
                }
            }

            if (response.decision() == DecisionType.FINAL) {
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "抱歉，处理您的请求超时了。请稍后再试或提供更具体的信息。";
            steps.add(StepRecord.finalAnswer(iteration, finalAnswer));
            memory.append(request.sessionId(), Message.of(Role.ASSISTANT, finalAnswer));
        }

        long latency = System.currentTimeMillis() - startTime;
        return new AgentResponse(finalAnswer, steps, totalUsage, latency);
    }
}
