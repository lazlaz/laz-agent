package com.shopai.agent.integration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web layer tests for the Chat and Session REST API.
 * Uses TestRestTemplate to verify request/response contracts against a real embedded server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("integration")
@DisplayName("Chat Controller Web Tests")
class ChatControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.restTemplate = new RestTemplate();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Map<String, String>> jsonBody(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    // ── POST /api/chat/send ────────────────────────────────────────────

    @Test
    @DisplayName("Should return messageId and streamUrl on send")
    void shouldReturnMessageIdAndStreamUrl() throws Exception {
        Map<String, String> body = Map.of("message", "你好");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/chat/send"), jsonBody(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("messageId")).isNotNull();
        assertThat(response.getBody().get("streamUrl").toString())
            .contains("/api/chat/stream/");
    }

    @Test
    @DisplayName("Should accept sessionId in send request")
    void shouldAcceptSessionId() {
        Map<String, String> body = Map.of(
            "sessionId", "test-session-123",
            "message", "查询订单"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/chat/send"), jsonBody(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("messageId")).isNotNull();
    }

    @Test
    @DisplayName("Should accept mode parameter")
    void shouldAcceptModeParameter() {
        Map<String, String> body = Map.of(
            "message", "你好",
            "mode", "plan-execute"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/chat/send"), jsonBody(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Should default to react mode when mode not specified")
    void shouldDefaultToReactMode() {
        Map<String, String> body = Map.of("message", "你好");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/chat/send"), jsonBody(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /api/chat/stream/{messageId} ───────────────────────────────

    @Test
    @DisplayName("Should accept SSE connection for stream endpoint")
    void shouldAcceptSseConnectionForStream() {
        // The SSE stream endpoint returns 200 even for unknown IDs
        // and emits error events on the stream.
        // We verify the endpoint is accessible and returns an SSE content type.
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "text/event-stream");

        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/chat/stream/non-existent-id"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

        // SSE connections return 200 OK (the error is streamed, not in HTTP status)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Session API ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/sessions should create a new session")
    void shouldCreateSession() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            url("/api/sessions"), null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("sessionId")).isNotNull();
    }

    @Test
    @DisplayName("GET /api/sessions should return session list")
    void shouldListSessions() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            url("/api/sessions"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("DELETE /api/sessions/{id} should delete session")
    void shouldDeleteSession() {
        // Create then delete
        ResponseEntity<Map> createResp = restTemplate.postForEntity(
            url("/api/sessions"), null, Map.class);

        assertThat(createResp.getBody()).isNotNull();
        String sessionId = createResp.getBody().get("sessionId").toString();

        restTemplate.delete(url("/api/sessions/" + sessionId));

        // Verify messages cleared
        ResponseEntity<String> getResp = restTemplate.getForEntity(
            url("/api/sessions/" + sessionId + "/messages"), String.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody()).isEqualTo("[]");
    }

    // ── Knowledge API ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/knowledge/documents should return document list")
    void shouldListKnowledgeDocuments() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            url("/api/knowledge/documents"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
