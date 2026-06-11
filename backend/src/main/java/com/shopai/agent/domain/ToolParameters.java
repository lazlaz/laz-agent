package com.shopai.agent.domain;

import java.util.List;
import java.util.Map;

public record ToolParameters(
    String type,
    Map<String, ParamSchema> properties,
    List<String> required
) {
    public ToolParameters {
        type = (type == null) ? "object" : type;
    }
}
