package com.shopai.agent.tracing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j {@link ChatModelListener} that creates OpenTelemetry spans
 * for every LLM call, following the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">GenAI Semantic Conventions</a>
 * and the
 * <a href="https://langfuse.com/docs/observability/integrations/opentelemetry">Langfuse OTel mapping</a>.
 *
 * <h3>Why both {@code gen_ai.*} and {@code langfuse.*} attributes?</h3>
 * <ul>
 *   <li>{@code gen_ai.*} — standard OTel semantic conventions, portable</li>
 *   <li>{@code langfuse.*} — take <strong>precedence</strong> in Langfuse UI
 *       (trace name, model name, usage details, etc.)</li>
 * </ul>
 *
 * <h3>Input/output visibility</h3>
 * Langfuse reads input/output from <strong>span attributes</strong>
 * ({@code langfuse.observation.input/output}), NOT from span events.
 * Span events are still emitted as supplementary detail but the primary
 * source for Langfuse display is always a span attribute.
 */
public class OtelChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(OtelChatModelListener.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ── GenAI semantic convention attribute keys ─────────────────────
    // (OTel standard — portable across backends)
    private static final AttributeKey<String> GEN_AI_OPERATION_NAME =
        AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> GEN_AI_SYSTEM =
        AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL =
        AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Long> GEN_AI_REQUEST_MAX_TOKENS =
        AttributeKey.longKey("gen_ai.request.max_tokens");
    private static final AttributeKey<Double> GEN_AI_REQUEST_TEMPERATURE =
        AttributeKey.doubleKey("gen_ai.request.temperature");
    private static final AttributeKey<Double> GEN_AI_REQUEST_TOP_P =
        AttributeKey.doubleKey("gen_ai.request.top_p");
    private static final AttributeKey<String> GEN_AI_RESPONSE_MODEL =
        AttributeKey.stringKey("gen_ai.response.model");
    private static final AttributeKey<String> GEN_AI_RESPONSE_ID =
        AttributeKey.stringKey("gen_ai.response.id");
    private static final AttributeKey<List<String>> GEN_AI_RESPONSE_FINISH_REASONS =
        AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");
    private static final AttributeKey<Long> GEN_AI_USAGE_INPUT_TOKENS =
        AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_USAGE_OUTPUT_TOKENS =
        AttributeKey.longKey("gen_ai.usage.output_tokens");

    // ── Langfuse-specific attribute keys ─────────────────────────────
    // (take precedence over gen_ai.* for display in Langfuse UI)
    private static final AttributeKey<String> LF_TRACE_NAME =
        AttributeKey.stringKey("langfuse.trace.name");
    private static final AttributeKey<String> LF_TRACE_INPUT =
        AttributeKey.stringKey("langfuse.trace.input");
    private static final AttributeKey<String> LF_TRACE_OUTPUT =
        AttributeKey.stringKey("langfuse.trace.output");
    private static final AttributeKey<String> LF_OBSERVATION_TYPE =
        AttributeKey.stringKey("langfuse.observation.type");
    private static final AttributeKey<String> LF_OBSERVATION_INPUT =
        AttributeKey.stringKey("langfuse.observation.input");
    private static final AttributeKey<String> LF_OBSERVATION_OUTPUT =
        AttributeKey.stringKey("langfuse.observation.output");
    private static final AttributeKey<String> LF_OBSERVATION_MODEL_NAME =
        AttributeKey.stringKey("langfuse.observation.model.name");
    private static final AttributeKey<String> LF_OBSERVATION_MODEL_PARAMETERS =
        AttributeKey.stringKey("langfuse.observation.model.parameters");
    private static final AttributeKey<String> LF_OBSERVATION_USAGE_DETAILS =
        AttributeKey.stringKey("langfuse.observation.usage_details");

    // Span event names (supplementary — NOT the primary source for Langfuse display)
    private static final String EVENT_PROMPT = "gen_ai.content.prompt";
    private static final String EVENT_COMPLETION = "gen_ai.content.completion";

    // Context attribute keys (passed between onRequest / onResponse / onError)
    private static final String CTX_SPAN = "otel.span";
    private static final String CTX_SCOPE = "otel.scope";

    private final Tracer tracer;

    public OtelChatModelListener(Tracer tracer) {
        this.tracer = tracer;
    }

    // ── ChatModelListener ────────────────────────────────────────────

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        var request = ctx.chatRequest();
        var params = request.parameters();
        String modelName = params != null ? params.modelName() : "unknown";
        String provider = ctx.modelProvider() != null ? ctx.modelProvider().name().toLowerCase() : "unknown";

        String spanName = "chat " + modelName;

        Span span = tracer.spanBuilder(spanName)
            .setStartTimestamp(Instant.now())
            .startSpan();

        // ── GenAI standard attributes (portable) ──
        span.setAttribute(GEN_AI_OPERATION_NAME, "chat");
        span.setAttribute(GEN_AI_SYSTEM, provider);
        span.setAttribute(GEN_AI_REQUEST_MODEL, modelName);

        if (params != null) {
            if (params.maxOutputTokens() != null) {
                span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, (long) params.maxOutputTokens());
            }
            if (params.temperature() != null) {
                span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, params.temperature().doubleValue());
            }
            if (params.topP() != null) {
                span.setAttribute(GEN_AI_REQUEST_TOP_P, params.topP().doubleValue());
            }
        }

        // ── Langfuse trace-level attributes ──
        span.setAttribute(LF_TRACE_NAME, spanName);
        span.setAttribute(LF_TRACE_INPUT, extractUserQuestion(request.messages()));

        // ── Langfuse observation attributes (higher priority for display) ──
        span.setAttribute(LF_OBSERVATION_TYPE, "generation");
        span.setAttribute(LF_OBSERVATION_MODEL_NAME, modelName);

        // Model parameters as structured JSON → Langfuse parses and displays in sidebar
        if (params != null) {
            span.setAttribute(LF_OBSERVATION_MODEL_PARAMETERS, modelParamsToJson(params));
        }

        // Input messages as OpenAI-format JSON → Langfuse displays as formatted input
        span.setAttribute(LF_OBSERVATION_INPUT, messagesToOpenAiJson(request.messages()));

        // Store span + scope for onResponse/onError
        Scope scope = span.makeCurrent();
        ctx.attributes().put(CTX_SPAN, span);
        ctx.attributes().put(CTX_SCOPE, scope);

        log.debug("OTel span started: {}", spanName);
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        Span span = (Span) ctx.attributes().get(CTX_SPAN);
        Scope scope = (Scope) ctx.attributes().get(CTX_SCOPE);

        if (span == null) {
            log.warn("No OTel span found in context — skipping trace");
            return;
        }

        try {
            var request = ctx.chatRequest();
            var response = ctx.chatResponse();
            var metadata = response.metadata();

            // ── GenAI standard attributes ──
            if (metadata != null) {
                if (metadata.modelName() != null) {
                    span.setAttribute(GEN_AI_RESPONSE_MODEL, metadata.modelName());
                }
                if (metadata.id() != null) {
                    span.setAttribute(GEN_AI_RESPONSE_ID, metadata.id());
                }
                if (metadata.tokenUsage() != null) {
                    var u = metadata.tokenUsage();
                    span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, (long) u.inputTokenCount());
                    span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, (long) u.outputTokenCount());

                    // Langfuse usage_details JSON → displayed as Usage in Langfuse UI
                    span.setAttribute(LF_OBSERVATION_USAGE_DETAILS, toJson(Map.of(
                        "input", u.inputTokenCount(),
                        "output", u.outputTokenCount()
                    )));
                }
                if (metadata.finishReason() != null) {
                    span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS,
                        List.of(metadata.finishReason().name().toLowerCase()));
                }
            }

            // ── Langfuse output (span attribute → displayed as Output in Langfuse UI) ──
            String completionText = response.aiMessage().text();
            span.setAttribute(LF_OBSERVATION_OUTPUT, toJson(Map.of(
                "role", "assistant",
                "content", completionText
            )));
            span.setAttribute(LF_TRACE_OUTPUT, completionText);

            // ── Span events (supplementary detail, not primary display source) ──
            span.addEvent(EVENT_PROMPT,
                Attributes.of(AttributeKey.stringKey("gen_ai.prompt"),
                    request.messages().toString()));
            span.addEvent(EVENT_COMPLETION,
                Attributes.of(AttributeKey.stringKey("gen_ai.completion"),
                    completionText));

            span.setStatus(StatusCode.OK);
            span.end();

            log.debug("OTel span completed: traceId={}, model={}, inTokens={}, outTokens={}",
                span.getSpanContext().getTraceId(),
                metadata != null ? metadata.modelName() : "n/a",
                metadata != null && metadata.tokenUsage() != null
                    ? metadata.tokenUsage().inputTokenCount() : 0,
                metadata != null && metadata.tokenUsage() != null
                    ? metadata.tokenUsage().outputTokenCount() : 0);

        } finally {
            if (scope != null) scope.close();
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        Span span = (Span) ctx.attributes().get(CTX_SPAN);
        Scope scope = (Scope) ctx.attributes().get(CTX_SCOPE);

        if (span == null) {
            log.warn("No OTel span found in context — skipping error trace");
            return;
        }

        try {
            var request = ctx.chatRequest();
            Throwable error = ctx.error();

            if (request != null) {
                span.addEvent(EVENT_PROMPT,
                    Attributes.of(AttributeKey.stringKey("gen_ai.prompt"),
                        request.messages().toString()));
            }

            span.setAttribute(AttributeKey.stringKey("error.type"),
                error != null ? error.getClass().getSimpleName() : "Unknown");
            if (error != null) {
                span.recordException(error);
            }
            span.setStatus(StatusCode.ERROR,
                error != null && error.getMessage() != null ? error.getMessage() : "LLM call failed");
            span.end();

            log.debug("OTel error span recorded: {}", error != null ? error.getMessage() : "n/a");

        } finally {
            if (scope != null) scope.close();
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────

    /**
     * Serializes LC4j messages to OpenAI-compatible JSON array.
     * <p>
     * Langfuse can parse this format and display messages with proper
     * role labels and content.
     */
    private static String messagesToOpenAiJson(List<ChatMessage> messages) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", roleFromMessage(msg));
            if (msg instanceof UserMessage um) {
                entry.put("content", um.singleText());
            } else if (msg instanceof AiMessage am) {
                entry.put("content", am.text());
            } else if (msg instanceof SystemMessage sm) {
                entry.put("content", sm.text());
            } else if (msg instanceof ToolExecutionResultMessage tm) {
                entry.put("content", tm.text());
            }
            list.add(entry);
        }
        return toJson(list);
    }

    /** Maps LC4j {@code ChatMessageType} to OpenAI role string. */
    private static String roleFromMessage(ChatMessage msg) {
        return switch (msg.type()) {
            case SYSTEM               -> "system";
            case USER                 -> "user";
            case AI                   -> "assistant";
            case TOOL_EXECUTION_RESULT -> "tool";
            case CUSTOM               -> "tool"; // custom messages treated as tool output
        };
    }

    /** Extracts the last user question for trace-level input display. */
    private static String extractUserQuestion(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage um) {
                return um.singleText();
            }
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).toString();
    }

    /** Serializes model parameters to a flat JSON object for Langfuse. */
    private static String modelParamsToJson(dev.langchain4j.model.chat.request.ChatRequestParameters params) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (params.temperature() != null)  map.put("temperature", params.temperature());
        if (params.topP() != null)         map.put("top_p", params.topP());
        if (params.maxOutputTokens() != null) map.put("max_tokens", params.maxOutputTokens());
        if (params.frequencyPenalty() != null) map.put("frequency_penalty", params.frequencyPenalty());
        if (params.presencePenalty() != null)  map.put("presence_penalty", params.presencePenalty());
        return toJson(map);
    }

    // ── Low-level JSON ────────────────────────────────────────────────

    private static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", obj, e);
            return obj != null ? obj.toString() : "null";
        }
    }
}
