package com.shopai.agent.domain;

import java.util.Map;

public record ToolResult(boolean success, String content, Map<String, Object> metadata) {
    public static ToolResult ok(String content) {
        return new ToolResult(true, content, Map.of());
    }

    public static ToolResult ok(String content, Map<String, Object> metadata) {
        return new ToolResult(true, content, metadata);
    }

    public static ToolResult fail(String content) {
        return new ToolResult(false, content, Map.of());
    }
}
