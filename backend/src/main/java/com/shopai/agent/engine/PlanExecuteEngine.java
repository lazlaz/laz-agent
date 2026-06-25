package com.shopai.agent.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Plan-and-Execute agent engine that decomposes complex user requests into
 * structured plans before executing them step by step.
 * <p>
 * <strong>Three-phase execution:</strong>
 * <ol>
 *   <li><b>PLANNING</b> — The LLM generates a structured {@link ExecutionPlan}
 *       (JSON) listing the steps needed to fulfill the user's request.</li>
 *   <li><b>EXECUTION</b> — Each step is executed sequentially: tool_call steps
 *       invoke registered tools via {@link ToolRegistry}; reasoning steps record
 *       the plan description as output.</li>
 *   <li><b>SYNTHESIS</b> — All step results plus the original question are sent
 *       to a streaming LLM to generate the final answer.</li>
 * </ol>
 * <p>
 * Events are emitted via a {@link Consumer} callback, decoupling this engine
 * from the SSE transport layer.
 */
@Component
public class PlanExecuteEngine {

    private static final Logger log = LoggerFactory.getLogger(PlanExecuteEngine.class);

    private static final String PLANNING_SYSTEM_PROMPT = """
        You are a planning agent for an e-commerce customer service AI. Given a user request and conversation history, create a step-by-step execution plan.

        Available tools:
        %s

        Output ONLY valid JSON (no markdown fences, no explanation):
        {
          "steps": [
            {"type": "tool_call", "tool": "<toolName>", "args": {"param1": "value1"}, "description": "What this step does"},
            {"type": "reasoning", "description": "Analyze results and think about next steps"}
          ]
        }

        Rules:
        - Max %d steps
        - Use reasoning steps between tool calls to analyze results
        - Each tool_call must have a valid tool name and all required args
        - Use Chinese for descriptions
        - If the user's request is simple (e.g., greeting, simple question), create a plan with just one reasoning step""";

    private static final String SYNTHESIS_SYSTEM_PROMPT = """
        You are a customer service AI for ShopAI, an electronics e-commerce platform.
        Your name is ShopAI Assistant.
        Based on the tool execution results below, synthesize a helpful, complete answer.

        ## User Question
        %s

        ## Step Execution Results
        %s

        Guidelines:
        - Answer in Chinese
        - Cite specific data from the results (product names, prices, order status, etc.)
        - If any tool calls failed, acknowledge limitations honestly
        - Be friendly and professional
        - If results are insufficient, suggest the user provide more details""";

    private final StreamingChatModel streamingModel;
    private final ChatModel planningModel;
    private final ChatMemoryStore memoryStore;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${shopai.agent.plan-execute.max-steps:10}")
    private int maxSteps;

    public PlanExecuteEngine(StreamingChatModel streamingModel,
                             ChatModel planningModel,
                             ChatMemoryStore memoryStore,
                             ToolRegistry toolRegistry) {
        this.streamingModel = streamingModel;
        this.planningModel = planningModel;
        this.memoryStore = memoryStore;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Executes the full Plan-Execute flow for a user message.
     *
     * @param sessionId   the conversation session ID
     * @param userMessage the user's latest message
     * @param emitter     callback for SSE events (never null)
     */
    public void execute(String sessionId, String userMessage,
                        Consumer<PlanExecuteEvent> emitter) {
        // Persist user message immediately
        persistUserMessage(sessionId, userMessage);

        try {
            // ── Phase 1: Planning ──────────────────────────────────────
            emitter.accept(new PlanExecuteEvent.PlanStart());
            log.info("[PlanExecute] Phase 1 — Planning for session={}", sessionId);

            ExecutionPlan plan = generatePlan(sessionId, userMessage);
            emitter.accept(new PlanExecuteEvent.PlanReady(plan));

            // ── Phase 2: Execution ─────────────────────────────────────
            log.info("[PlanExecute] Phase 2 — Executing {} steps", plan.steps().size());
            List<StepResult> results = executeSteps(plan, emitter);

            // ── Phase 3: Synthesis ─────────────────────────────────────
            log.info("[PlanExecute] Phase 3 — Synthesizing final answer");
            emitter.accept(new PlanExecuteEvent.SynthesisStart());
            synthesizeAnswer(userMessage, results, emitter);

        } catch (Exception e) {
            log.error("[PlanExecute] Fatal error in session={}: {}", sessionId, e.getMessage(), e);
            emitter.accept(new PlanExecuteEvent.PlanError("engine", e.getMessage()));
        }
    }

    // ── Phase 1: Planning ───────────────────────────────────────────────

    private ExecutionPlan generatePlan(String sessionId, String userMessage) {
        String toolsInfo = buildToolsInfo();
        String systemPrompt = String.format(PLANNING_SYSTEM_PROMPT, toolsInfo, maxSteps);

        // Load recent history for context
        List<ChatMessage> history = memoryStore.getMessages(sessionId);
        String historyText = history.stream()
            .map(m -> m.type() + ": " + m.toString())
            .collect(Collectors.joining("\n"));

        String fullPrompt = systemPrompt + "\n\n" +
            "Conversation history:\n" + historyText + "\n\n" +
            "User request: " + userMessage;

        int retries = 0;
        while (retries < 2) {
            try {
                String rawJson = planningModel.chat(fullPrompt);
                log.debug("[PlanExecute] Raw planning response: {}", rawJson);
                ExecutionPlan plan = parsePlan(rawJson);
                validatePlan(plan);
                log.info("[PlanExecute] Plan generated: {} steps", plan.steps().size());
                return plan;
            } catch (Exception e) {
                retries++;
                log.warn("[PlanExecute] Planning attempt {} failed: {}", retries, e.getMessage());
                if (retries >= 2) {
                    // Fallback: create a single reasoning step
                    log.info("[PlanExecute] Falling back to single reasoning step");
                    return new ExecutionPlan(List.of(
                        new PlanStep(0, "reasoning", "直接回答用户问题", null, Map.of())
                    ));
                }
                fullPrompt += "\n\nPrevious response was invalid JSON. Please output ONLY valid JSON this time.";
            }
        }
        // Unreachable, but kept for compiler
        return new ExecutionPlan(List.of(
            new PlanStep(0, "reasoning", "直接回答用户问题", null, Map.of())
        ));
    }

    /** Builds a human-readable description of all registered tools. */
    private String buildToolsInfo() {
        StringBuilder sb = new StringBuilder();
        Set<String> names = toolRegistry.toolNames();
        for (String name : names) {
            ToolRegistry.ToolEntry entry = toolRegistry.get(name);
            if (entry != null) {
                sb.append(String.format("- %s: %s\n  Parameters: %s\n",
                    name, entry.toolDescription(), entry.paramSchema()));
            }
        }
        return sb.toString();
    }

    /** Parses the LLM's JSON output into an ExecutionPlan. */
    private ExecutionPlan parsePlan(String rawJson) throws JsonProcessingException {
        // Strip markdown fences if present
        String cleaned = rawJson.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("```[a-z]*\n?", "").replaceAll("\n```", "").trim();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> root = mapper.readValue(cleaned, Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawSteps = (List<Map<String, Object>>) root.get("steps");

        if (rawSteps == null || rawSteps.isEmpty()) {
            throw new IllegalArgumentException("Plan JSON must contain a non-empty 'steps' array");
        }

        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < rawSteps.size(); i++) {
            Map<String, Object> s = rawSteps.get(i);
            String type = (String) s.getOrDefault("type", "reasoning");
            String description = (String) s.getOrDefault("description", "");
            String tool = (String) s.get("tool");

            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) s.getOrDefault("args", Map.of());

            steps.add(new PlanStep(i, type, description, tool, args));
        }
        return new ExecutionPlan(steps);
    }

    /** Validates that all tool_call steps reference known tools. */
    private void validatePlan(ExecutionPlan plan) {
        Set<String> knownTools = toolRegistry.toolNames();
        for (PlanStep step : plan.steps()) {
            if ("tool_call".equals(step.type())) {
                if (step.tool() == null || step.tool().isBlank()) {
                    throw new IllegalArgumentException("tool_call step " + step.index() + " missing 'tool' field");
                }
                if (!knownTools.contains(step.tool())) {
                    log.warn("[PlanExecute] Unknown tool in plan: '{}', known: {}", step.tool(), knownTools);
                    // Don't throw — let execution handle unknown tool gracefully
                }
            }
        }
    }

    // ── Phase 2: Execution ───────────────────────────────────────────────

    private List<StepResult> executeSteps(ExecutionPlan plan,
                                          Consumer<PlanExecuteEvent> emitter) {
        List<StepResult> results = new ArrayList<>();

        for (PlanStep step : plan.steps()) {
            emitter.accept(new PlanExecuteEvent.StepStart(step.index(), step));

            StepResult result;
            try {
                if ("tool_call".equals(step.type())) {
                    String output = toolRegistry.invoke(step.tool(), step.args());
                    result = new StepResult(step.index(), "tool_call", step.tool(), output, true);
                    log.info("[PlanExecute] Step {} ({}) success — {} chars output",
                        step.index(), step.tool(), output.length());
                } else {
                    // reasoning step
                    result = new StepResult(step.index(), "reasoning", null,
                        step.description(), true);
                }
            } catch (Exception e) {
                log.warn("[PlanExecute] Step {} ({}) failed: {}", step.index(), step.tool(), e.getMessage());
                result = new StepResult(step.index(), step.type(), step.tool(),
                    "Tool execution failed: " + e.getMessage(), false);
            }

            results.add(result);
            emitter.accept(new PlanExecuteEvent.StepDone(step.index(), result));
        }

        return results;
    }

    // ── Phase 3: Synthesis ───────────────────────────────────────────────

    private void synthesizeAnswer(String userMessage, List<StepResult> results,
                                  Consumer<PlanExecuteEvent> emitter) {
        String resultsText = formatStepResults(results);
        String systemPrompt = String.format(SYNTHESIS_SYSTEM_PROMPT, userMessage, resultsText);

        try {
            streamingModel.chat(systemPrompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    emitter.accept(new PlanExecuteEvent.SynthesisToken(token));
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    var usage = response.metadata().tokenUsage();
                    int inputTokens = (usage != null && usage.inputTokenCount() != null) ? usage.inputTokenCount() : 0;
                    int outputTokens = (usage != null && usage.outputTokenCount() != null) ? usage.outputTokenCount() : 0;

                    String fullContent = response.aiMessage().text();
                    emitter.accept(new PlanExecuteEvent.SynthesisDone(fullContent, inputTokens, outputTokens));
                }

                @Override
                public void onError(Throwable error) {
                    log.error("[PlanExecute] Synthesis streaming error", error);
                    emitter.accept(new PlanExecuteEvent.PlanError("synthesis",
                        error.getMessage() != null ? error.getMessage() : "Unknown synthesis error"));
                }
            });
        } catch (Exception e) {
            log.error("[PlanExecute] Synthesis setup error", e);
            emitter.accept(new PlanExecuteEvent.PlanError("synthesis", e.getMessage()));
        }
    }

    private String formatStepResults(List<StepResult> results) {
        StringBuilder sb = new StringBuilder();
        for (StepResult r : results) {
            sb.append(String.format("[Step %d] %s %s — %s: %s\n",
                r.stepIndex(),
                r.type(),
                r.tool() != null ? "(" + r.tool() + ")" : "",
                r.success() ? "SUCCESS" : "FAILED",
                r.output()));
        }
        return sb.toString();
    }

    // ── Persistence helpers ──────────────────────────────────────────────

    private void persistUserMessage(String sessionId, String userMessage) {
        try {
            List<ChatMessage> all = new ArrayList<>(memoryStore.getMessages(sessionId));
            all.add(UserMessage.from(userMessage));
            memoryStore.updateMessages(sessionId, all);
        } catch (Exception e) {
            log.warn("[PlanExecute] Failed to persist user message: {}", e.getMessage());
        }
    }
}
