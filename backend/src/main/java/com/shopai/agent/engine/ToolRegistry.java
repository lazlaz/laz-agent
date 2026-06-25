package com.shopai.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflective tool registry for Plan-and-Execute mode.
 * <p>
 * Maps tool method names (e.g., "searchProducts") to callable {@link ToolEntry}
 * instances. When the LLM planning phase generates a tool_call step, this registry
 * resolves the tool name and invokes the corresponding Spring bean method with
 * type-coerced arguments.
 * <p>
 * Tool beans are registered explicitly via {@link #register(Object)}. The registry
 * discovers {@code @Tool}-annotated methods via reflection, reads {@code @P} parameter
 * annotations to build parameter name indices, and uses Jackson for argument type coercion.
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Scans the given bean for {@code @Tool}-annotated methods and registers them.
     * The method name (e.g., "searchProducts") becomes the tool identifier used in
     * planning prompts and step execution.
     */
    public void register(Object bean) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            Tool toolAnn = method.getAnnotation(Tool.class);
            if (toolAnn == null) continue;

            String toolName = method.getName();
            Map<String, Integer> paramIndex = new ConcurrentHashMap<>();
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length; i++) {
                P pAnn = params[i].getAnnotation(P.class);
                String paramName = pAnn != null ? extractParamName(pAnn.value()) : params[i].getName();
                paramIndex.put(paramName, i);
            }

            ToolEntry entry = new ToolEntry(bean, method, paramIndex, toolAnn, params);
            tools.put(toolName, entry);
            log.info("[ToolRegistry] Registered tool '{}' ({}) — {} params",
                toolName, method.getDeclaringClass().getSimpleName(), params.length);
        }
    }

    /**
     * Extracts the first meaningful word from a {@code @P} description
     * (e.g., "搜索关键词，匹配产品名和描述" → "keyword").
     * Falls back to using the description text directly if parsing fails.
     */
    private String extractParamName(String pDescription) {
        if (pDescription == null || pDescription.isBlank()) return "arg";
        // Take the first segment before punctuation or space as the canonical param name
        String cleaned = pDescription.split("[，。、；,.;\\s]")[0];
        return cleaned.length() > 20 ? "arg" : cleaned;
    }

    /** Returns a registered tool entry, or {@code null} if not found. */
    public ToolEntry get(String toolName) {
        return tools.get(toolName);
    }

    /** All registered tool names (for prompt building). */
    public Set<String> toolNames() {
        return tools.keySet();
    }

    /**
     * Invokes a registered tool, coercing argument types via Jackson.
     *
     * @param toolName the method name of the tool (e.g., "searchProducts")
     * @param args     named argument map from the LLM planning JSON
     * @return the tool's string result
     * @throws IllegalArgumentException if the tool is unknown
     * @throws Exception                if invocation fails
     */
    public String invoke(String toolName, Map<String, Object> args) throws Exception {
        ToolEntry entry = tools.get(toolName);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        Object[] callArgs = new Object[entry.paramCount];
        for (int i = 0; i < entry.paramCount; i++) {
            String paramName = entry.paramName(i);
            Object raw = args.get(paramName);
            if (raw == null) {
                // Try fallback: look for positional keys like "0", "1", ...
                raw = args.get(String.valueOf(i));
            }
            if (raw == null) {
                callArgs[i] = null;
                continue;
            }

            Class<?> targetType = entry.paramType(i);
            if (targetType.isInstance(raw)) {
                callArgs[i] = raw;
            } else {
                // Coerce via Jackson (handles Number → Double, String → whatever, etc.)
                String json = mapper.writeValueAsString(raw);
                callArgs[i] = mapper.readValue(json, targetType);
            }
        }

        log.info("[ToolRegistry] Invoking {}({})", toolName, args);
        return (String) entry.method.invoke(entry.instance, callArgs);
    }

    // ── Inner types ──────────────────────────────────────────────────────

    /** Holds reflective metadata for a registered tool. */
    public static class ToolEntry {
        final Object instance;
        final Method method;
        final Map<String, Integer> paramIndex;
        final Tool annotation;
        final Parameter[] params;
        final int paramCount;

        ToolEntry(Object instance, Method method, Map<String, Integer> paramIndex,
                  Tool annotation, Parameter[] params) {
            this.instance = instance;
            this.method = method;
            this.paramIndex = paramIndex;
            this.annotation = annotation;
            this.params = params;
            this.paramCount = params.length;
        }

        /** Parameter name at the given index. Returns null if unknown. */
        public String paramName(int index) {
            for (var entry : paramIndex.entrySet()) {
                if (entry.getValue() == index) return entry.getKey();
            }
            return null;
        }

        /** Parameter type at the given index. */
        public Class<?> paramType(int index) {
            return params[index].getType();
        }

        public String toolDescription() {
            String[] vals = annotation.value();
            return vals.length > 0 ? String.join(" ", vals) : "";
        }

        /** Generates a parameter schema string for LLM planning prompts. */
        public String paramSchema() {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                String name = paramName(i);
                String type = params[i].getType().getSimpleName();
                P pAnn = params[i].getAnnotation(P.class);
                String desc = pAnn != null ? pAnn.value() : "";
                sb.append(String.format("\"%s\": %s // %s", name, type, desc));
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
