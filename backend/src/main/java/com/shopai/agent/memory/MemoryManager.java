package com.shopai.agent.memory;

import com.shopai.agent.domain.Message;

import java.util.List;

public interface MemoryManager {
    List<Message> loadHistory(String sessionId, int maxMessages);
    void append(String sessionId, Message msg);
    void clear(String sessionId);
}
