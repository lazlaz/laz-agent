package com.shopai.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTool {

    @Tool("执行数学计算，支持加减乘除和括号。例如: '2+3*4' 或 '100*0.9'")
    public String calculate(
        @P("数学表达式，支持 + - * / 和括号") String expression) {

        if (expression == null || expression.isBlank()) {
            return "请提供 expression 参数";
        }

        try {
            javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = mgr.getEngineByName("JavaScript");
            Object result = engine.eval(expression);
            return expression + " = " + result;
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }
}
