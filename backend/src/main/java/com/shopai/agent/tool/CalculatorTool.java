package com.shopai.agent.tool;

import com.shopai.agent.domain.ParamSchema;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolParameters;
import com.shopai.agent.domain.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CalculatorTool {

    public ToolDefinition definition() {
        return new ToolDefinition(
            "CalculatorTool",
            "执行数学计算。参数: expression (数学表达式，如 '2+3*4' 或 '100*0.9')",
            new ToolParameters(
                "object",
                Map.of(
                    "expression", new ParamSchema("string", "数学表达式，支持 + - * / 和括号")
                ),
                List.of("expression")
            ),
            this::execute
        );
    }

    private ToolResult execute(Map<String, Object> args) {
        String expression = (String) args.get("expression");
        if (expression == null || expression.isBlank()) {
            return ToolResult.fail("请提供 expression 参数");
        }

        try {
            javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = mgr.getEngineByName("JavaScript");
            Object result = engine.eval(expression);
            return ToolResult.ok(expression + " = " + result);
        } catch (Exception e) {
            return ToolResult.fail("计算错误: " + e.getMessage());
        }
    }
}
