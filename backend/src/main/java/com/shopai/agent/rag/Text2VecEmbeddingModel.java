package com.shopai.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Text2VecEmbeddingModel implements EmbeddingModel, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(Text2VecEmbeddingModel.class);

    @Value("${shopai.rag.embedding.model-path}")
    private String modelPath;

    @Value("${shopai.rag.embedding.model-name}")
    private String modelName;

    @Value("${shopai.rag.embedding.sidecar-port:9876}")
    private int sidecarPort;

    @Value("${shopai.rag.embedding.sidecar-startup-timeout-seconds:30}")
    private int startupTimeoutSeconds;

    private Process pythonProcess;
    private final RestTemplate restTemplate = new RestTemplate();
    private String sidecarUrl;

    @PostConstruct
    public void startSidecar() {
        sidecarUrl = "http://127.0.0.1:" + sidecarPort;
        try {
            log.info("Starting embedding sidecar: model={}, port={}", modelName, sidecarPort);
            // Resolve sidecar script relative to working directory;
            // when run via 'cd backend && mvn spring-boot:run' the script is in cwd;
            // when run from project root the script lives under backend/.
            java.io.File scriptDir = new java.io.File(System.getProperty("user.dir"));
            if (!new java.io.File(scriptDir, "embedding_sidecar.py").exists()) {
                java.io.File backendDir = new java.io.File(scriptDir, "backend");
                if (new java.io.File(backendDir, "embedding_sidecar.py").exists()) {
                    scriptDir = backendDir;
                }
            }
            ProcessBuilder pb = new ProcessBuilder(
                "python", "embedding_sidecar.py"
            );
            pb.directory(scriptDir);
            pb.environment().put("MODEL_PATH", modelPath);
            pb.environment().put("MODEL_NAME", modelName);
            pb.environment().put("PORT", String.valueOf(sidecarPort));
            pb.redirectErrorStream(true);
            pythonProcess = pb.start();

            // Wait for health endpoint to be ready
            long deadline = System.currentTimeMillis() + startupTimeoutSeconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Map<?, ?> health = restTemplate.getForObject(sidecarUrl + "/health", Map.class);
                    if (health != null && "ok".equals(health.get("status"))) {
                        log.info("Embedding sidecar ready: {}", health);
                        return;
                    }
                } catch (RestClientException ignored) {
                    // sidecar not ready yet
                }
                Thread.sleep(1000);
            }
            throw new IllegalStateException("Embedding sidecar failed to start within " + startupTimeoutSeconds + "s");
        } catch (Exception e) {
            log.error("Failed to start embedding sidecar", e);
            throw new RuntimeException("Failed to start embedding sidecar", e);
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<String> texts = segments.stream().map(TextSegment::text).toList();
        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                sidecarUrl + "/encode",
                Map.of("texts", texts),
                Map.class
            );

            if (response == null || !response.containsKey("embeddings")) {
                throw new RuntimeException("Empty or invalid response from embedding sidecar");
            }

            @SuppressWarnings("unchecked")
            List<List<Double>> rawEmbeddings = (List<List<Double>>) response.get("embeddings");
            List<Embedding> embeddings = new ArrayList<>();
            for (List<Double> raw : rawEmbeddings) {
                float[] vector = new float[raw.size()];
                for (int i = 0; i < raw.size(); i++) {
                    vector[i] = raw.get(i).floatValue();
                }
                embeddings.add(Embedding.from(vector));
            }
            return Response.from(embeddings);
        } catch (RestClientException e) {
            log.error("Embedding sidecar call failed", e);
            throw new RuntimeException("Embedding service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            log.info("Shutting down embedding sidecar");
            pythonProcess.destroyForcibly();
        }
    }
}
