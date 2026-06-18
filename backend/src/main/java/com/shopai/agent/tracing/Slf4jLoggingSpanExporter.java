package com.shopai.agent.tracing;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * A {@link SpanExporter} that logs every exported span to SLF4J at INFO level.
 * <p>
 * This is a diagnostic tool — it proves that spans are being created and
 * passed to the export pipeline.  If you see span logs here but nothing in
 * Langfuse, the problem is in the OTLP exporter (auth, network, endpoint).
 */
public class Slf4jLoggingSpanExporter implements SpanExporter {

    private static final Logger log = LoggerFactory.getLogger(Slf4jLoggingSpanExporter.class);

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        for (SpanData span : spans) {
            log.info("[OTel-Export] span=\"{}\"  traceId={}  spanId={}  status={}  "
                    + "attrs={}  events={}  duration={}ms",
                span.getName(),
                span.getTraceId(),
                span.getSpanId(),
                span.getStatus().getStatusCode(),
                span.getAttributes().size(),
                span.getEvents().size(),
                (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000
            );
        }
        log.info("[OTel-Export] batch complete — {} span(s) logged", spans.size());
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        log.debug("[OTel-Export] flush requested");
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        log.info("[OTel-Export] shutdown complete");
        return CompletableResultCode.ofSuccess();
    }
}
