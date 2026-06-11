package com.shopai.agent.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Message(
    String id,
    Role role,
    String content,
    Map<String, Object> metadata,
    Instant timestamp
) {
    public Message {
        metadata = Map.copyOf(metadata);
    }

    public static Message of(Role role, String content) {
        return new Message(
            UUID.randomUUID().toString(),
            role,
            content,
            Map.of(),
            Instant.now()
        );
    }

    public static Message of(Role role, String content, Map<String, Object> metadata) {
        return new Message(
            UUID.randomUUID().toString(),
            role,
            content,
            metadata,
            Instant.now()
        );
    }
}
