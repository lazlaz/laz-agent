package com.shopai.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.domain.Message;
import com.shopai.agent.domain.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class H2MemoryManager implements MemoryManager {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public H2MemoryManager(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Message> loadHistory(String sessionId, int maxMessages) {
        String sql = """
            SELECT id, session_id, role, content, metadata, created_at
            FROM message
            WHERE session_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        List<Message> messages = jdbc.query(sql, this::mapMessage, sessionId, maxMessages);
        // Reverse to chronological order (query returns DESC)
        List<Message> chronological = new ArrayList<>(messages);
        java.util.Collections.reverse(chronological);
        return chronological;
    }

    @Override
    public void append(String sessionId, Message msg) {
        String sql = """
            INSERT INTO message (id, session_id, role, content, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        String metadataJson;
        try {
            metadataJson = mapper.writeValueAsString(msg.metadata());
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }
        jdbc.update(sql,
            msg.id() != null ? msg.id() : UUID.randomUUID().toString(),
            sessionId,
            msg.role().name(),
            msg.content(),
            metadataJson,
            Timestamp.from(msg.timestamp() != null ? msg.timestamp() : Instant.now())
        );

        jdbc.update(
            "MERGE INTO conversation (id, session_id, title) VALUES (?, ?, ?)",
            UUID.randomUUID().toString(), sessionId, "会话 " + sessionId.substring(0, 8)
        );
    }

    @Override
    public void clear(String sessionId) {
        jdbc.update("DELETE FROM message WHERE session_id = ?", sessionId);
    }

    private Message mapMessage(ResultSet rs, int rowNum) throws java.sql.SQLException {
        String id = rs.getString("id");
        Role role = Role.valueOf(rs.getString("role"));
        String content = rs.getString("content");
        String metadataStr = rs.getString("metadata");
        Map<String, Object> metadata;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(metadataStr, Map.class);
            metadata = parsed;
        } catch (Exception e) {
            metadata = Map.of();
        }
        Instant ts = rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toInstant()
            : Instant.now();
        return new Message(id, role, content, metadata, ts);
    }
}
