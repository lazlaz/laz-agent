package com.shopai.agent.tracing;

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
import java.util.List;

/**
 * LangChain4j {@link ChatModelListener} that creates OpenTelemetry spans
 * for every LLM call, following the
 * <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">GenAI Semantic Conventions</a>.
 * <p>
 * Spans are exported via OTLP to the configured backend (e.g. Langfuse).
 */
public class OtelChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(OtelChatModelListener.class);

    // ── GenAI semantic convention attribute keys ─────────────────────
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

    // Span event names
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

        // Build span name
        String spanName = "chat " + modelName;

        Span span = tracer.spanBuilder(spanName)
            .setStartTimestamp(Instant.now())
            .startSpan();

        // GenAI attributes
        var attrs = Attributes.builder()
            .put(GEN_AI_OPERATION_NAME, "chat")
            .put(GEN_AI_SYSTEM, provider)
            .put(GEN_AI_REQUEST_MODEL, modelName);

        if (params != null) {
            if (params.maxOutputTokens() != null) {
                attrs.put(GEN_AI_REQUEST_MAX_TOKENS, (long) params.maxOutputTokens());
            }
            if (params.temperature() != null) {
                attrs.put(GEN_AI_REQUEST_TEMPERATURE, params.temperature().doubleValue());
            }
            if (params.topP() != null) {
                attrs.put(GEN_AI_REQUEST_TOP_P, params.topP().doubleValue());
            }
        }

        span.setAllAttributes(attrs.build());

        // Store span + scope in context attributes for onResponse/onError
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

            // Response attributes
            if (metadata != null) {
                if (metadata.modelName() != null) {
                    span.setAttribute(GEN_AI_RESPONSE_MODEL, metadata.modelName());
                }
                if (metadata.id() != null) {
                    span.setAttribute(GEN_AI_RESPONSE_ID, metadata.id());
                }
                if (metadata.tokenUsage() != null) {
                    var usage = metadata.tokenUsage();
                    span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, (long) usage.inputTokenCount());
                    span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, (long) usage.outputTokenCount());
                }
                if (metadata.finishReason() != null) {
                    span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS,
                        List.of(metadata.finishReason().name().toLowerCase()));
                }
            }

            // Record prompt as a span event with the full message content
            String promptText = request.messages().toString();
            span.addEvent(EVENT_PROMPT,
                Attributes.of(AttributeKey.stringKey("gen_ai.prompt"), promptText));

            // Record completion as a span event
            String completionText = response.aiMessage().toString();
            span.addEvent(EVENT_COMPLETION,
                Attributes.of(AttributeKey.stringKey("gen_ai.completion"), completionText));

            span.setStatus(StatusCode.OK);
            span.end();

            log.debug("OTel span completed: traceId={}, model={}, inTokens={}, outTokens={}",
                span.getSpanContext().getTraceId(),
                metadata != null ? metadata.modelName() : "n/a",
                metadata != null && metadata.tokenUsage() != null ? metadata.tokenUsage().inputTokenCount() : 0,
                metadata != null && metadata.tokenUsage() != null ? metadata.tokenUsage().outputTokenCount() : 0);

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

            // Record the prompt that caused the error
            if (request != null) {
                span.addEvent(EVENT_PROMPT,
                    Attributes.of(AttributeKey.stringKey("gen_ai.prompt"), request.messages().toString()));
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
}
