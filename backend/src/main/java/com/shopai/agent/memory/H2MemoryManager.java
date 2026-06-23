package com.shopai.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class H2MemoryManager implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(H2MemoryManager.class);

    private final JdbcTemplate jdbc;

    /**
     * Uses LangChain4j's pre-configured ObjectMapper that registers all necessary
     * Jackson mixins (including {@code @JsonDeserialize(builder=...)} on
     * {@link ToolExecutionRequest}). Without these mixins, Jackson cannot
     * deserialize {@link ToolExecutionRequest} because it uses a builder pattern
     * with a private constructor.
     */
    private final ObjectMapper mapper = JacksonChatMessageJsonCodec
        .chatMessageJsonMapperBuilder()
        .build();

    public H2MemoryManager(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        String sql = """
            SELECT id, session_id, msg_type, content, tool_requests_json, tool_name, tool_request_id, created_at
            FROM agent_message
            WHERE session_id = ?
            ORDER BY created_at ASC
            """;

        // Two-pass: first collect raw rows, then rebuild with context
        List<RawRow> rows = jdbc.query(sql, this::readRawRow, sessionId);

        List<ChatMessage> messages = new ArrayList<>();
        // Track toolName → toolCallId so TOOL_RESULT rows can find their matching id
        Map<String, String> toolNameToId = new HashMap<>();

        for (RawRow row : rows) {
            ChatMessage msg = buildMessage(row, toolNameToId);
            messages.add(msg);

            // After building the AI message, register its tool call ids for subsequent TOOL_RESULT rows
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    toolNameToId.put(req.name(), req.id());
                }
            }
        }

        return messages;
    }

    /**
     * Reads a raw row without reconstructing ChatMessage (column values only).
     */
    private RawRow readRawRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RawRow(
            rs.getString("msg_type"),
            rs.getString("content") == null ? "" : rs.getString("content"),
            rs.getString("tool_requests_json"),
            rs.getString("tool_name"),
            rs.getString("tool_request_id")
        );
    }

    /**
     * Rebuilds a ChatMessage from a raw row, using toolNameToId to fix up
     * TOOL_RESULT messages whose tool_request_id was NULL (e.g. old data).
     */
    private ChatMessage buildMessage(RawRow row, Map<String, String> toolNameToId) {
        String msgType = row.msgType;
        String content = row.content;
        String toolRequestsJson = row.toolRequestsJson;
        String toolName = row.toolName;
        String toolRequestId = row.toolRequestId;

        return switch (msgType) {
            case "SYSTEM" -> SystemMessage.from(content);
            case "USER" -> UserMessage.from(content);
            case "AI" -> {
                if (toolRequestsJson != null && !toolRequestsJson.isEmpty() && !"[]".equals(toolRequestsJson)) {
                    try {
                        List<ToolExecutionRequest> requests = mapper.readValue(toolRequestsJson,
                            mapper.getTypeFactory().constructCollectionType(List.class,
                                ToolExecutionRequest.class));
                        yield new AiMessage(content, requests);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize tool_requests_json, falling back to plain AI message", e);
                        yield AiMessage.from(content);
                    }
                } else {
                    yield AiMessage.from(content);
                }
            }
            case "TOOL_RESULT" -> {
                // Resolve the tool execution request id.  If the column is present use it;
                // otherwise fall back to the most recent AI tool call with the same tool name.
                String resolvedId = toolRequestId;
                if (resolvedId == null && toolName != null) {
                    resolvedId = toolNameToId.get(toolName);
                    if (resolvedId == null) {
                        log.warn("TOOL_RESULT has tool_name='{}' but no matching ToolExecutionRequest — "
                            + "generating fallback id; this may cause API errors", toolName);
                        resolvedId = UUID.randomUUID().toString();
                    }
                } else if (resolvedId == null) {
                    resolvedId = UUID.randomUUID().toString();
                }
                yield ToolExecutionResultMessage.from(resolvedId,
                    toolName != null ? toolName : "unknown",
                    content);
            }
            default -> UserMessage.from(content);
        };
    }

    /** Holds column values for a single agent_message row. */
    private record RawRow(
        String msgType,
        String content,
        String toolRequestsJson,
        String toolName,
        String toolRequestId
    ) {}

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        // Delete old messages and re-insert (ChatMemoryStore contract)
        jdbc.update("DELETE FROM agent_message WHERE session_id = ?", sessionId);

        // Insert with sequence-based timestamp offset to preserve deterministic order.
        // Without the offset, all rows get the same Instant.now() and ORDER BY created_at
        // may return them in a scrambled order — which breaks DeepSeek's requirement that
        // every "tool" message must follow an "assistant" message with matching tool_calls.
        Instant base = Instant.now();
        for (int i = 0; i < messages.size(); i++) {
            insertMessage(sessionId, messages.get(i), base.plusNanos(i));
        }

        // Ensure conversation record exists and set a meaningful title.
        // Only update the title once: from "新会话" → first user message.
        // Subsequent calls leave the title untouched.
        String title = extractTitle(messages);
        boolean exists = Boolean.TRUE.equals(
            jdbc.queryForObject("SELECT COUNT(*) > 0 FROM conversation WHERE session_id = ?",
                                 Boolean.class, sessionId));
        if (!exists) {
            jdbc.update(
                "INSERT INTO conversation (id, session_id, title) VALUES (?, ?, ?)",
                UUID.randomUUID().toString(), sessionId, title);
        } else {
            jdbc.update(
                "UPDATE conversation SET title = ? WHERE session_id = ? AND title = '新会话'",
                title, sessionId);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        jdbc.update("DELETE FROM agent_message WHERE session_id = ?", memoryId.toString());
    }

    private void insertMessage(String sessionId, ChatMessage msg, Instant createdAt) {
        String sql = """
            INSERT INTO agent_message (id, session_id, msg_type, content, tool_requests_json, tool_name, tool_request_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        String msgType;
        String content;
        String toolRequestsJson = null;
        String toolName = null;
        String toolRequestId = null;

        switch (msg.type()) {
            case SYSTEM -> {
                msgType = "SYSTEM";
                content = ((SystemMessage) msg).text();
            }
            case USER -> {
                msgType = "USER";
                content = ((UserMessage) msg).singleText();
            }
            case AI -> {
                msgType = "AI";
                AiMessage aiMsg = (AiMessage) msg;
                content = aiMsg.text();
                if (aiMsg.hasToolExecutionRequests()) {
                    try {
                        toolRequestsJson = mapper.writeValueAsString(aiMsg.toolExecutionRequests());
                    } catch (JsonProcessingException e) {
                        toolRequestsJson = "[]";
                    }
                }
            }
            case TOOL_EXECUTION_RESULT -> {
                msgType = "TOOL_RESULT";
                ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) msg;
                content = toolMsg.text();
                toolName = toolMsg.toolName();
                toolRequestId = toolMsg.id();
            }
            default -> {
                msgType = "UNKNOWN";
                content = msg.toString();
            }
        }

        jdbc.update(sql,
            UUID.randomUUID().toString(),
            sessionId,
            msgType,
            content,
            toolRequestsJson,
            toolName,
            toolRequestId,
            Timestamp.from(createdAt)
        );
    }

    /**
     * Extracts a conversation title from the first USER message in the list.
     * Caps at 40 characters to keep the sidebar tidy.
     */
    private String extractTitle(List<ChatMessage> messages) {
        for (ChatMessage msg : messages) {
            if (msg instanceof UserMessage userMsg) {
                String text = userMsg.singleText();
                if (text != null && !text.isBlank()) {
                    return text.length() > 40 ? text.substring(0, 40) + "…" : text;
                }
            }
        }
        return "新会话";
    }

}
