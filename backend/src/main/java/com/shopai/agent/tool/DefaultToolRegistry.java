package com.shopai.agent.tool;

import com.shopai.agent.domain.ToolCall;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    @Override
    public void register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    @Override
    public Optional<ToolDefinition> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public ToolResult execute(ToolCall call) {
        ToolDefinition tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.fail("Tool not found: " + call.name());
        }
        try {
            return tool.handler().apply(call.arguments());
        } catch (Exception e) {
            return ToolResult.fail("Tool execution error: " + e.getMessage());
        }
    }

    @Override
    public List<ToolDefinition> listAll() {
        return List.copyOf(tools.values());
    }
}
