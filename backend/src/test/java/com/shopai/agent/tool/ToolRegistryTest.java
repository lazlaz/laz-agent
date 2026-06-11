package com.shopai.agent.tool;

import com.shopai.agent.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private DefaultToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        registry.register(new ToolDefinition(
            "test_tool",
            "A test tool",
            new ToolParameters("object", Map.of("input", new ParamSchema("string", "test input")), List.of("input")),
            args -> ToolResult.ok("Got: " + args.get("input"))
        ));
    }

    @Test
    void shouldRegisterAndRetrieveTool() {
        var tool = registry.get("test_tool");
        assertTrue(tool.isPresent());
        assertEquals("test_tool", tool.get().name());
    }

    @Test
    void shouldReturnEmptyForUnknownTool() {
        assertTrue(registry.get("unknown").isEmpty());
    }

    @Test
    void shouldExecuteTool() {
        var result = registry.execute(new ToolCall("test_tool", Map.of("input", "hello")));
        assertTrue(result.success());
        assertEquals("Got: hello", result.content());
    }

    @Test
    void shouldReturnErrorForUnknownTool() {
        var result = registry.execute(new ToolCall("unknown", Map.of()));
        assertFalse(result.success());
        assertTrue(result.content().contains("not found"));
    }

    @Test
    void shouldListAllTools() {
        assertEquals(1, registry.listAll().size());
    }
}
