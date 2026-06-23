package com.shopai.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CalculatorTool {

    private static final Logger log = LoggerFactory.getLogger(CalculatorTool.class);

    @Tool("执行数学计算，支持加减乘除和括号。例如: '2+3*4' 或 '100*0.9'")
    public String calculate(
        @P("数学表达式，支持 + - * / 和括号") String expression) {

        log.info("[Calculator] 表达式: {}", expression);

        if (expression == null || expression.isBlank()) {
            return "请提供 expression 参数";
        }

        try {
            javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = mgr.getEngineByName("JavaScript");
            Object result = engine.eval(expression);
            String output = expression + " = " + result;
            log.info("[Calculator] 结果: {}", output);
            return output;
        } catch (Exception e) {
            log.warn("[Calculator] 失败: {}", e.getMessage());
            return "计算错误: " + e.getMessage();
        }
    }
}
