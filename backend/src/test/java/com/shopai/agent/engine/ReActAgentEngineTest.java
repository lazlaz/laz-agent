package com.shopai.agent.engine;

import com.shopai.agent.domain.*;
import com.shopai.agent.llm.LlmAdapter;
import com.shopai.agent.memory.MemoryManager;
import com.shopai.agent.prompt.PromptEngine;
import com.shopai.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReActAgentEngineTest {

    @Mock private LlmAdapter llm;
    @Mock private PromptEngine prompt;
    @Mock private MemoryManager memory;
    @Mock private ToolRegistry tools;

    private ReActAgentEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReActAgentEngine(llm, prompt, memory, tools);
        ReflectionTestUtils.setField(engine, "maxIterations", 3);
        ReflectionTestUtils.setField(engine, "maxHistoryMessages", 20);
    }

    @Test
    void shouldReturnDirectFinalAnswer() {
        var llmResponse = new LlmResponse("您好，有什么可以帮您？", DecisionType.FINAL, null, new TokenUsage(10, 8));
        when(llm.chat(any())).thenReturn(llmResponse);
        when(prompt.build(eq("react-system"), any())).thenReturn(
            new ChatRequest("sys prompt", List.of(), List.of())
        );
        when(memory.loadHistory(anyString(), anyInt())).thenReturn(List.of());

        AgentResponse response = engine.execute(new AgentRequest("s1", "你好"));

        assertEquals("您好，有什么可以帮您？", response.content());
        assertEquals(1, response.steps().size());
        assertEquals(DecisionType.FINAL, llmResponse.decision());
    }

    @Test
    void shouldExecuteToolThenFinish() {
        var call1 = new LlmResponse("Need tool", DecisionType.TOOL_CALL,
            new ToolCall("test_tool", Map.of("p", "v")), new TokenUsage(5, 3));
        var call2 = new LlmResponse("Done!", DecisionType.FINAL, null, new TokenUsage(8, 5));

        when(llm.chat(any())).thenReturn(call1, call2);
        when(prompt.build(eq("react-system"), any())).thenReturn(
            new ChatRequest("sys", List.of(), List.of())
        );
        when(memory.loadHistory(anyString(), anyInt())).thenReturn(List.of());
        when(tools.execute(any())).thenReturn(ToolResult.ok("tool result data"));

        AgentResponse response = engine.execute(new AgentRequest("s2", "use tool"));

        assertEquals("Done!", response.content());
        assertEquals(3, response.steps().size()); // TOOL_CALL + TOOL_RESULT + FINAL
        verify(tools).execute(any());
    }
}
