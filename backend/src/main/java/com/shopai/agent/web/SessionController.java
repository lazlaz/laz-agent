package com.shopai.agent.web;

import com.shopai.agent.memory.MemoryManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final JdbcTemplate jdbc;
    private final MemoryManager memory;

    public SessionController(JdbcTemplate jdbc, MemoryManager memory) {
        this.jdbc = jdbc;
        this.memory = memory;
    }

    @GetMapping
    public List<Map<String, Object>> listSessions() {
        return jdbc.queryForList(
            "SELECT session_id, title, created_at FROM conversation ORDER BY created_at DESC"
        );
    }

    @GetMapping("/{sessionId}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable String sessionId) {
        return jdbc.queryForList(
            "SELECT id, role, content, metadata, created_at FROM message WHERE session_id = ? ORDER BY created_at ASC",
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
        memory.clear(sessionId);
        jdbc.update("DELETE FROM conversation WHERE session_id = ?", sessionId);
        return Map.of("status", "deleted");
    }
}
