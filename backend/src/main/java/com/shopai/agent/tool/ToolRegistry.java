package com.shopai.agent.tool;

import com.shopai.agent.domain.ToolCall;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolResult;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    void register(ToolDefinition tool);
    Optional<ToolDefinition> get(String name);
    ToolResult execute(ToolCall call);
    List<ToolDefinition> listAll();
}
