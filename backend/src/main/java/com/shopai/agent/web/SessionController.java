package com.shopai.agent.web;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final JdbcTemplate jdbc;
    private final ChatMemoryStore memoryStore;

    public SessionController(JdbcTemplate jdbc, ChatMemoryStore memoryStore) {
        this.jdbc = jdbc;
        this.memoryStore = memoryStore;
    }

    @GetMapping
    public List<Map<String, Object>> listSessions() {
        // Deduplicate by session_id — keeps the earliest row for each session.
        // H2 supports ROW_NUMBER() window function.
        return jdbc.queryForList("""
            SELECT session_id, title, created_at FROM (
                SELECT session_id, title, created_at,
                       ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY created_at ASC) AS rn
                FROM conversation
            ) WHERE rn = 1 ORDER BY created_at DESC
            """);
    }

    @GetMapping("/{sessionId}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable String sessionId) {
        return jdbc.queryForList(
            "SELECT id, msg_type, content, tool_name, created_at FROM agent_message WHERE session_id = ? ORDER BY created_at ASC",
            sessionId
        );
    }

    @PostMapping
    public Map<String, String> createSession() {
        String sessionId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO conversation (id, session_id, title) VALUES (?, ?, ?)",
            UUID.randomUUID().toString(), sessionId, "新会话"
        );
        return Map.of("sessionId", sessionId);
    }

    @DeleteMapping("/{sessionId}")
    public Map<String, String> deleteSession(@PathVariable String sessionId) {
        memoryStore.deleteMessages(sessionId);
        jdbc.update("DELETE FROM conversation WHERE session_id = ?", sessionId);
        return Map.of("status", "deleted");
    }
}
