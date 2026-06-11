package com.shopai.agent.domain;

import java.util.Map;

public record ToolCall(String name, Map<String, Object> arguments) {}
