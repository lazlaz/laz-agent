package com.shopai.agent.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Initializes OpenTelemetry with explicit exporter configuration.
 * <p>
 * Two exporters are wired:
 * <ol>
 *   <li>{@link Slf4jLoggingSpanExporter} — logs every span locally so you can
 *       verify spans are being created (check application log for
 *       {@code [OTel-Export]})</li>
 *   <li>{@link OtlpHttpSpanExporter} — sends spans to Langfuse via OTLP
 *       HTTP/protobuf</li>
 * </ol>
 * <p>
 * Langfuse credentials are read from {@code shopai.langfuse.*} in
 * {@code application-local.yml} (git-ignored).
 */
@Configuration
public class OpenTelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryConfig.class);
    public static final String INSTRUMENTATION_NAME = "com.shopai.agent.langchain4j";

    private OpenTelemetrySdk openTelemetrySdk;

    // ── Beans ──────────────────────────────────────────────────────────

    @Bean
    public OpenTelemetry openTelemetry(Environment env) {
        // 1. Bridge JUL → SLF4J so OTel SDK internal logs are visible
        installJulBridge();

        // 2. Read config
        String host = env.getProperty("shopai.langfuse.host", "https://jp.cloud.langfuse.com");
        String publicKey = env.getProperty("shopai.langfuse.public-key");
        String secretKey = env.getProperty("shopai.langfuse.secret-key");

        // 3. Build local logging exporter (always active — proves spans exist)
        SpanExporter loggingExporter = new Slf4jLoggingSpanExporter();
        log.info("Logging exporter registered — spans will be logged to SLF4J");

        // 4. Build OTLP exporter (to Langfuse)
        SpanExporter otlpExporter;
        if (publicKey != null && !publicKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            String endpoint = host + "/api/public/otel/v1/traces";
            String credentials = publicKey + ":" + secretKey;
            String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            otlpExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("Authorization", authHeader)
                .setTimeout(Duration.ofSeconds(10))
                .build();

            log.info("OTLP exporter → {} (auth: keyId={})", endpoint, publicKey.substring(0, Math.min(12, publicKey.length())) + "...");
        } else {
            log.warn("Langfuse keys NOT configured — OTLP export disabled. "
                + "Fill in shopai.langfuse.public-key and shopai.langfuse.secret-key in application-local.yml");
            otlpExporter = null;
        }

        // 5. Composite exporter (log locally + send to Langfuse)
        SpanExporter compositeExporter;
        if (otlpExporter != null) {
            compositeExporter = SpanExporter.composite(loggingExporter, otlpExporter);
        } else {
            compositeExporter = loggingExporter;
        }

        // 6. Resource (service identity)
        Resource resource = Resource.builder()
            .put("service.name", "shopai-agent")
            .put("service.version", "0.1.0")
            .build();

        // 7. Tracer provider with aggressive batch export (for debugging)
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(compositeExporter)
                .setScheduleDelay(1, TimeUnit.SECONDS)   // export every 1s
                .setMaxExportBatchSize(1)                 // export even single spans
                .setMaxQueueSize(2048)
                .build())
            .build();

        // 8. SDK
        openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();

        log.info("OpenTelemetry SDK ready — traces will appear in Langfuse within ~1s of each LLM call");
        return openTelemetrySdk;
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    // ── Clean shutdown ─────────────────────────────────────────────────

    @PreDestroy
    public void shutdown() {
        if (openTelemetrySdk != null) {
            log.info("Shutting down OTel SDK — flushing pending spans...");
            var result = openTelemetrySdk.getSdkTracerProvider().shutdown();
            result.join(5, TimeUnit.SECONDS);
            log.info("OTel SDK shut down complete");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Installs the JUL → SLF4J bridge so that any
     * {@code java.util.logging} messages from the OTel SDK are routed to
     * SLF4J / Logback and visible in the application log.
     */
    private static void installJulBridge() {
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            java.util.logging.Logger.getLogger("io.opentelemetry").setLevel(java.util.logging.Level.FINE);
            log.debug("JUL → SLF4J bridge installed for OTel SDK logging");
        }
    }
}
