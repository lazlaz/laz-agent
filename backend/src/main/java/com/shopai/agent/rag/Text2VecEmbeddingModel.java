package com.shopai.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LC4j {@link EmbeddingModel} backed by a user-managed Python embedding sidecar.
 * <p>
 * The sidecar is started independently by the user (see {@code embedding_sidecar.py}).
 * This component simply connects to it over HTTP and implements the standard
 * LangChain4j embedding interface so the rest of the RAG pipeline works unchanged.
 */
@Component
public class Text2VecEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(Text2VecEmbeddingModel.class);

    private final String sidecarUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public Text2VecEmbeddingModel(
        @Value("${shopai.rag.embedding.sidecar-host:127.0.0.1}") String sidecarHost,
        @Value("${shopai.rag.embedding.sidecar-port:9876}") int sidecarPort
    ) {
        this.sidecarUrl = "http://" + sidecarHost + ":" + sidecarPort;
        log.info("Embedding sidecar configured at {}", sidecarUrl);
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
}
